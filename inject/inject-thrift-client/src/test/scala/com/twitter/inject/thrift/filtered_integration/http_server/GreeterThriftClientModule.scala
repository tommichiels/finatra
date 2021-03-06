package com.twitter.inject.thrift.filtered_integration.http_server

import com.twitter.greeter.thriftscala.Greeter.{Bye, Hi}
import com.twitter.greeter.thriftscala.{Greeter, InvalidOperation}
import com.twitter.inject.thrift.filters.ThriftClientFilterBuilder
import com.twitter.inject.thrift.modules.FilteredThriftClientModule
import com.twitter.util._
import scala.util.control.NonFatal

object GreeterThriftClientModule
    extends FilteredThriftClientModule[Greeter[Future], Greeter.ServiceIface] {

  override val label = "greeter-thrift-client"
  override val dest = "flag!greeter-thrift-service"
  override val sessionAcquisitionTimeout = 1.minute.toDuration

  override def filterServiceIface(
    serviceIface: Greeter.ServiceIface,
    filter: ThriftClientFilterBuilder
  ) = {

    serviceIface.copy(
      hi = filter
        .method(Hi)
        .withTimeout(2.minutes)
        .withConstantRetry(shouldRetryResponse = {
          case Throw(InvalidOperation(_)) => true
          case Return(success) => success == "ERROR"
          case Throw(NonFatal(_)) => true
        }, start = 50.millis, retries = 3)
        .withRequestTimeout(1.minute)
        .filtered[HiLoggingThriftClientFilter]
        .andThen(serviceIface.hi),
      bye = filter
        .method(Bye)
        .withExponentialRetry(
          shouldRetryResponse = PossiblyRetryableExceptions,
          start = 50.millis,
          multiplier = 2,
          retries = 3
        )
        .withRequestTimeout(1.minute)
        .andThen(serviceIface.bye)
    )
  }
}
