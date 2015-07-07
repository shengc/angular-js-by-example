package resource

import org.http4s._
import org.http4s.server._
import org.http4s.dsl._
import org.http4s.headers._

import scalaz.stream._
import scalaz.concurrent._

import scala.concurrent.duration._

trait Route { self =>
  def svc: HttpService
  def orElse(route: Route): Route = new Route {
    val svc = self.svc orElse route.svc
  }
}

class CacheRoute extends Route {
  private val cache = ResourceCache()
  
  val svc: HttpService = HttpService {
    case req if req.pathInfo.startsWith("/static") =>
      cache.getResource("", req.pathInfo, req)
    case req @ GET -> Root / path =>
      val pth = if (path.contains('.')) path else path + ".html"
      cache.getResource("/views", pth, req)
    case req if req.pathInfo.endsWith("/") =>
      svc(req.withPathInfo(req.pathInfo + "index.html")).map(_.getOrElse(NotFound(req.pathInfo).run))
  }
}

class TestRoute extends Route {
  val svc: HttpService = HttpService {
    case GET -> Root / "ping" =>
      Ok("pong")
    case GET -> Root / "chunked" =>
      val proc = Process.constant("test").zipWithIndex.map({ case (msg, i) => s"$msg - $i" }).zip(time.awakeEvery(100 millis)).map(_._1).take(5)
      Ok(proc).putHeaders(`Transfer-Encoding`(TransferCoding.chunked))
  }
  
  implicit lazy val sch = Strategy.DefaultTimeoutScheduler
}