import zio._
import zio.http._
import zio.json._

// --- MODÈLE DE DONNÉES ---
case class Message(text: String)

object Message {
  implicit val codec: JsonCodec[Message] = DeriveJsonCodec.gen[Message]
}

// --- APPLICATION ---
object Main extends ZIOAppDefault {

  def createRoutes(memory: Ref[List[Message]]) = Routes(
    
    // Route 1 (GET) : Lire les messages
    Method.GET / "messages" -> handler {
      memory.get.map { messages =>
        Response.json(messages.toJson)
      }
    },

    // Route 2 (POST) : Ajouter un message
    Method.POST / "messages" -> handler { (req: Request) =>
      req.body.asString.flatMap { bodyStr =>
        bodyStr.fromJson[Message] match {
          case Left(error) => 
            ZIO.succeed(Response.text(s"Mauvais JSON: $error").status(Status.BadRequest))
          
          case Right(msg) => 
            memory.update(list => list :+ msg).as(Response.text("Message ajouté !"))
        }
      }
      .catchAll(e => ZIO.succeed(Response.text("Erreur serveur").status(Status.InternalServerError)))
    }
  )

  // --- DÉMARRAGE DU SERVEUR ---
  override val run =
    for {
      memory <- Ref.make(List.empty[Message])
      _      <- ZIO.logInfo("Serveur lancé sur http://localhost:8080")
      _      <- Server.serve(createRoutes(memory)).provide(Server.default)
    } yield ()
}