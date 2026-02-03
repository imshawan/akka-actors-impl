import AskWithCounter.AskCounter.{StartDecrement, StartIncrement}
import AskWithCounter.Counter.{Decrement, Increment, QueryCount}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.util.Timeout

import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success, Try}

object AskWithCounter extends App {
  object Counter {
    sealed trait CounterCommand
    case class QueryCount(replyTo: ActorRef[RespondCount]) extends CounterCommand
    case class RespondCount(num: Int)
    case object Increment extends CounterCommand
    case object Decrement extends CounterCommand

    private var counter: Int = 0

    def apply(): Behavior[CounterCommand] = Behaviors.receiveMessage {
      case QueryCount(replyTo) =>
        replyTo ! RespondCount(counter)
        Behaviors.same

      case Increment =>
        counter += 1
        Behaviors.same

      case Decrement =>
        counter -= 1
        Behaviors.same
    }
  }

  object AskCounter {
    sealed trait CommandInput
    case object StartIncrement extends CommandInput
    case object StartDecrement extends CommandInput
    private case class ReplyIncrement(value: Try[Counter.RespondCount]) extends CommandInput
    private case class ReplyDecrement(value: Try[Counter.RespondCount]) extends CommandInput

    def apply(counterRef: ActorRef[Counter.CounterCommand]): Behavior[CommandInput] = Behaviors.setup {
      context =>
        implicit val timeout: Timeout = 3.seconds

        Behaviors.receiveMessage {
          case StartIncrement =>

            counterRef ! Increment

            context.ask(counterRef, ref => QueryCount(ref)) {
              case Success(value) => ReplyIncrement(Success(value))
              case Failure(exception) => ReplyIncrement(Failure(exception))
            }
            Behaviors.same

          case StartDecrement =>

            counterRef ! Decrement

            context.ask(counterRef, ref => QueryCount(ref)) {
              case Success(value) => ReplyDecrement(Success(value))
              case Failure(exception) => ReplyDecrement(Failure(exception))
            }
            Behaviors.same

          case ReplyIncrement(Success(Counter.RespondCount(value))) =>
            context.log.info(s"After incrementing: $value")
            Behaviors.same

          case ReplyIncrement(Failure(ex)) =>
            context.log.warn(s"Exception during incrementing: ${ex.getMessage}")
            Behaviors.same

          case ReplyDecrement(Success(Counter.RespondCount(value))) =>
            context.log.info(s"After decrementing: $value")
            Behaviors.same

          case ReplyDecrement(Failure(ex)) =>
            context.log.warn(s"Exception during decrementing: ${ex.getMessage}")
            Behaviors.same
        }
    }
  }

  private val mainActor = Behaviors.setup[Unit] {
    context =>
      val counter = context.spawn(Counter(), "Counter")
      val asker = context.spawn(AskCounter(counter), "Asker")

      asker ! StartIncrement
      asker ! StartDecrement

      Behaviors.empty
  }

  val system = ActorSystem(mainActor, "MainActor")
}
