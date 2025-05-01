import akka.actor.typed._
import akka.actor.typed.scaladsl._
import scala.concurrent.duration._

object TimerActor extends App {
  sealed trait Command
  case object Start extends Command
  case object Timeout extends Command

  def apply(): Behavior[Command] = Behaviors.withTimers { timers =>
    Behaviors.receive { (context, message) =>
      message match {
        case Start =>
          context.log.info("Timer started.")
          timers.startSingleTimer("timeout", Timeout, 3.seconds)
          Behaviors.same

        case Timeout =>
          context.log.info("Timeout occurred!")
          Behaviors.stopped
      }
    }
  }

  val system = ActorSystem(TimerActor(), "TimerSystem")
  system ! TimerActor.Start

}
