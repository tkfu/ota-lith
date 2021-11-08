package com.advancedtelematic.treehub

import akka.http.scaladsl.Http
import akka.http.scaladsl.server.{Directives, Route}
import com.advancedtelematic.libats.http.{BootApp, NamespaceDirectives}
import com.advancedtelematic.libats.http.LogDirectives._
import com.advancedtelematic.libats.http.VersionDirectives._
import com.advancedtelematic.libats.http.tracing.Tracing
import com.advancedtelematic.libats.messaging.MessageBus
import com.advancedtelematic.libats.slick.db.{BootMigrations, CheckMigrations, DatabaseConfig}
import com.advancedtelematic.libats.slick.monitoring.DatabaseMetrics
import com.advancedtelematic.metrics.prometheus.PrometheusMetricsSupport
import com.advancedtelematic.metrics.{AkkaHttpRequestMetrics, MetricsSupport}
import com.advancedtelematic.treehub.client._
import com.advancedtelematic.treehub.daemon.StaleObjectArchiveActor
import com.advancedtelematic.treehub.delta_store.{LocalDeltaStorage, S3DeltaStorage}
import com.advancedtelematic.treehub.http.TreeHubRoutes
import com.advancedtelematic.treehub.object_store.{LocalFsBlobStore, ObjectStore, S3BlobStore}
import com.advancedtelematic.treehub.repo_metrics.UsageMetricsRouter

object Boot extends BootApp with Directives with Settings with VersionInfo
  with BootMigrations
  with DatabaseConfig
  with MetricsSupport
  with DatabaseMetrics
  with AkkaHttpRequestMetrics
  with PrometheusMetricsSupport
  with CheckMigrations {

  implicit val _db = db

  log.info(s"Starting $version on http://$host:$port")

  val deviceRegistry = new DeviceRegistryHttpClient(deviceRegistryUri, deviceRegistryMyApi)

  val namespaceExtractor = NamespaceDirectives.defaultNamespaceExtractor

  lazy val objectStorage = {
    if(useS3) {
      log.info("Using s3 storage for object blobs")
      S3BlobStore(s3Credentials, allowRedirectsToS3)
    } else {
      log.info(s"Using local storage a t$localStorePath for object blobs")
      LocalFsBlobStore(localStorePath.resolve("object-storage"))
    }
  }

  lazy val deltaStorage = {
    if(useS3) {
      log.info("Using s3 storage for object blobs")
      S3DeltaStorage(s3Credentials)
    } else {
      log.info(s"Using local storage at $localStorePath for object blobs")
      new LocalDeltaStorage(localStorePath.resolve("delta-storage"))
    }
  }

  val objectStore = new ObjectStore(objectStorage)
  val msgPublisher = MessageBus.publisher(system, config)
  val tracing = Tracing.fromConfig(config, projectName)

  val usageHandler = system.actorOf(UsageMetricsRouter(msgPublisher, objectStore), "usage-router")

  if(objectStorage.supportsOutOfBandStorage) {
    system.actorOf(StaleObjectArchiveActor.withBackOff(objectStorage, staleObjectExpireAfter, autoStart = true), "stale-objects-archiver")
  }

  val routes: Route =
    (versionHeaders(version) & requestMetrics(metricRegistry) & logResponseMetrics(projectName)) {
      prometheusMetricsRoutes ~
        tracing.traceRequests { _ =>
          new TreeHubRoutes(
            namespaceExtractor,
            msgPublisher,
            objectStore,
            deltaStorage,
            usageHandler
          ).routes
        }
    }

  Http().newServerAt(host, port).bindFlow(routes)
}