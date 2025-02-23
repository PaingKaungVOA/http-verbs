/*
 * Copyright 2023 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.http

import java.net.ConnectException
import java.util.concurrent.TimeoutException

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.http.logging.{ConnectionTracing, LoggingDetails}

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}

trait CommonHttpBehaviour extends ScalaFutures with Matchers with AnyWordSpecLike {

  case class TestClass(foo: String, bar: Int)
  implicit val tcreads: OFormat[TestClass] = Json.format[TestClass]

  case class TestRequestClass(baz: String, bar: Int)
  implicit val trcreads: OFormat[TestRequestClass] = Json.format[TestRequestClass]

  implicit val hc: HeaderCarrier = HeaderCarrier()
  val testBody: String           = "testBody"
  val testRequestBody: String    = "testRequestBody"
  val url: String                = "http://some.url"

  def response(returnValue: Option[String] = None, statusCode: Int = 200): Future[HttpResponse] =
    Future.successful(HttpResponse(
      status = statusCode,
      body   = returnValue.getOrElse("")
    ))

  val defaultHttpResponse: Future[HttpResponse] = response()

  def anErrorMappingHttpCall(verb: String, httpCall: (String, Future[HttpResponse]) => Future[_]): Unit = {
    s"throw a GatewayTimeout exception when the HTTP $verb throws a TimeoutException" in {

      val url: String = "http://some.nonexistent.url"

      val e = httpCall(url, Future.failed(new TimeoutException("timeout"))).failed.futureValue

      e            should be(a[GatewayTimeoutException])
      e.getMessage should startWith(verb)
      e.getMessage should include(url)
    }

    s"throw a BadGateway exception when the HTTP $verb throws a ConnectException" in {

      val url: String = "http://some.nonexistent.url"

      val e = httpCall(url, Future.failed(new ConnectException("timeout"))).failed.futureValue

      e            should be(a[BadGatewayException])
      e.getMessage should startWith(verb)
      e.getMessage should include(url)
    }
  }

  def aTracingHttpCall[T <: ConnectionTracingCapturing](verb: String, method: String, httpBuilder: => T)(
    httpAction: T => Future[_]): Unit =
    s"trace exactly once when the HTTP $verb calls $method" in {
      val http = httpBuilder
      httpAction(http).futureValue
      http.traceCalls         should have size 1
      http.traceCalls.head._1 shouldBe verb
    }

}

trait ConnectionTracingCapturing extends ConnectionTracing {

  val traceCalls: mutable.Buffer[(String, String)] = mutable.Buffer[(String, String)]()

  override def withTracing[T](method: String, uri: String)(
    body: => Future[T])(implicit ld: LoggingDetails, ec: ExecutionContext): Future[T] = {
    traceCalls += ((method, uri))
    body
  }
}
