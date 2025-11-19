import zio._
import zio.http._
import zio.http.ChannelEvent.{Read, UserEvent, UserEventTriggered}
import zio.json._

// --- MODÈLE DE DONNÉES ---
case class Message(text: String)

object Message {
  implicit val codec: JsonCodec[Message] = DeriveJsonCodec.gen[Message]
}

// --- APPLICATION ---
object Main extends ZIOAppDefault {

  def createRoutes(memory: Ref[List[Message]]): Routes[Any, Response] = {

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
      Method.GET / "ws" -> handler(socketApp.toResponse)
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
