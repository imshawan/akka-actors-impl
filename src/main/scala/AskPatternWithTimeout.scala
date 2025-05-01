import akka.actor.typed._
import akka.actor.typed.scaladsl._
import akka.util.Timeout

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.{Failure, Success}

object AskPatternWithTimeout extends App {

  object Responder {
    sealed trait Command
    case class Query(replyTo: ActorRef[Response]) extends Command
    case class Response(data: String)

    def apply(): Behavior[Command] = Behaviors.receiveMessage {
      case Query(replyTo) =>
        replyTo ! Response("Here is the answer.")
        Behaviors.same
    }
  }

  object Asker {
    sealed trait Command
    case object Start extends Command

    def apply(responder: ActorRef[Responder.Command]): Behavior[Command] =
      Behaviors.receive { (context, message) =>
        implicit val timeout: Timeout = 4.seconds

        message match {
          case Start =>
            context.ask(responder, Responder.Query) {
              case Success(Responder.Response(data)) =>
                context.log.info(s"Got response: $data")
                Start // or just Behaviors.same
              case Failure(ex) =>
                context.log.error("Ask failed", ex)
                Start
            }
            Behaviors.same
        }
      }
  }


  val system = ActorSystem(Behaviors.empty, "AskSystem")
  val responder = system.systemActorOf(Responder(), "Responder")
  val asker = system.systemActorOf(Asker(responder), "Asker")
  asker ! Asker.Start
}
