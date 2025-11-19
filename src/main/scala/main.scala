import zio._
import zio.http._
import zio.http.ChannelEvent.{Read, UserEvent, UserEventTriggered}
import zio.json._
import scala.io.Source
import zio.http.{Body, Headers}


// --- MODÈLE DE DONNÉES ---
case class Message(text: String)

object Message {
  implicit val codec: JsonCodec[Message] = DeriveJsonCodec.gen[Message]
}

// --- APPLICATION ---
object Main extends ZIOAppDefault {

  def createRoutes(memory: Ref[List[Message]]): Routes[Any, Response] = {

    // --- WebSocket /ws ---
    val socketApp: WebSocketApp[Any] =
      Handler.webSocket { channel =>
        channel.receiveAll {
          case UserEventTriggered(UserEvent.HandshakeComplete) =>
            channel.send(Read(WebSocketFrame.text("Connecté au WebSocket /ws ✅")))

          case Read(WebSocketFrame.Text(text)) =>
            text.fromJson[Message] match {
              case Right(msg) =>
                memory.update(_ :+ msg) *>
                  channel.send(Read(WebSocketFrame.text(s"Message enregistré: ${msg.text}")))

              case Left(err) =>
                channel.send(Read(WebSocketFrame.text(s"JSON invalide: $err")))
            }

          case Read(WebSocketFrame.Close(status, reason)) =>
            ZIO.logInfo(s"WebSocket fermé: $status - $reason")

          case _ =>
            ZIO.unit
        }
      }

    Routes(

      // --- Route 1 : GET /messages ---
      Method.GET / "messages" -> handler {
        memory.get.map { messages =>
          Response.json(messages.toJson)
        }
      },

      // --- Route 2 : POST /messages ---
      Method.POST / "messages" -> handler { (req: Request) =>
        req.body.asString.flatMap { bodyStr =>
          bodyStr.fromJson[Message] match {
            case Left(error) =>
              ZIO.succeed(
                Response.text(s"Mauvais JSON: $error").status(Status.BadRequest)
              )

            case Right(msg)  =>
              memory
                .update(_ :+ msg)
                .as(Response.text("Message ajouté !").status(Status.Created))
          }
        }.catchAll(_ =>
          ZIO.succeed(Response.text("Erreur serveur").status(Status.InternalServerError))
        )
      },

      // --- Route 3 : WebSocket /ws ---
      Method.GET / "ws" -> handler(socketApp.toResponse),

      // --- Route 4 : /monitor (sert server-monitor.html depuis les resources) ---
// --- Route 4 : /monitor (sert server-monitor.html depuis resources) ---
      Method.GET / "monitor" -> handler {
        ZIO
          .attempt {
            val src = scala.io.Source.fromResource("server-monitor.html")(scala.io.Codec.UTF8)
            try src.mkString
            finally src.close()
          }
          .map { html =>
            Response(
              status = Status.Ok,
              headers = Headers("Content-Type" -> "text/html; charset=utf-8"),
              body = Body.fromString(html)
            )
          }
          .catchAll { err =>
            ZIO.succeed(
              Response
                .text(s"Erreur chargement monitor: ${err.getMessage}")
                .status(Status.InternalServerError)
            )
          }
      }
    )
  }

  // --- DÉMARRAGE DU SERVEUR ---
  override val run =
    for {
      memory <- Ref.make(List.empty[Message])
      _      <- ZIO.logInfo("Serveur lancé sur http://localhost:8080")
      _      <- Server.serve(createRoutes(memory)).provide(Server.default)
    } yield ()
}
