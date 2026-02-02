import akka.actor.typed._
import akka.actor.typed.scaladsl._
import akka.util.Timeout
import scala.util.{Failure, Success}
import scala.concurrent.duration.DurationInt

object AskPatternWithTimeout extends App {

  object Responder {
    sealed trait AskCommandInput
    case class Query(replyTo: ActorRef[Response]) extends AskCommandInput
    case class Response(message: String)

    def apply(): Behavior[AskCommandInput] = Behaviors.receiveMessage {
      case Query(replyTo) =>
        replyTo ! Response("This response is from RESPONDER")
        Behaviors.same
    }
  }

  object Asker {
    sealed trait CommandInput
    case object StartMainActor extends CommandInput
    // We need a wrapper to handle the async response back in our own message loop
    private case class WrappedResponse(result: scala.util.Try[Responder.Response]) extends CommandInput

    def apply(responderActorRef: ActorRef[Responder.AskCommandInput]): Behavior[CommandInput] =
      Behaviors.setup { context =>
        implicit val timeout: Timeout = 3.seconds

        Behaviors.receiveMessage {
          case StartMainActor =>
            // Correct 'ask' syntax: (target, replyTo => Request(replyTo))(mapResultToOwnMessage)
            context.ask(responderActorRef, ref => Responder.Query(ref)) {
              case Success(res) => WrappedResponse(Success(res))
              case Failure(ex)  => WrappedResponse(Failure(ex))
            }
            Behaviors.same

          case WrappedResponse(Success(Responder.Response(data))) =>
            context.log.info(s"Message received from ask: '$data'")
            Behaviors.same

          case WrappedResponse(Failure(exception)) =>
            context.log.warn(s"Exception occurred: ${exception.getMessage}")
            Behaviors.same
        }
      }
  }

  // Define a Guardian behavior to bootstrap the system
  val rootBehavior = Behaviors.setup[Unit] { context =>
    val responseActor = context.spawn(Responder(), "ResponderActor")
    val askActor = context.spawn(Asker(responseActor), "AskActor")

    askActor ! Asker.StartMainActor
    Behaviors.empty
  }

  val system = ActorSystem(rootBehavior, "AskActorSystem")
}