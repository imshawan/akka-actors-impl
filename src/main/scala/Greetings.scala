import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors

//#greetings-actor
object Greetings {
  final case class Greet(whom: String, replyTo: ActorRef[Greeted])
  final case class Greeted(whom: String, from: ActorRef[Greet])

  def apply(): Behavior[Greet] = Behaviors.receive { (context, message) =>
    context.log.info("Hello {}!", message.whom)
    message.replyTo ! Greeted(message.whom, context.self)
    Behaviors.same
  }
}
//#greetings-actor

//#greetings-bot
object GreetingsBot {

  def apply(max: Int): Behavior[Greetings.Greeted] = {
    bot(0, max)
  }

  private def bot(greetingCounter: Int, max: Int): Behavior[Greetings.Greeted] =
    Behaviors.receive { (context, message) =>
      val n = greetingCounter + 1
      context.log.info("Greeting {} for {}", n, message.whom)
      if (n == max) {
        Behaviors.stopped
      } else {
        message.from ! Greetings.Greet(message.whom, context.self)
        bot(n, max)
      }
    }
}
//#greetings-bot

object Printer {
  final case class PrintMessage(message: String)

  def apply(): Behavior[PrintMessage] = {
    Behaviors.receive{
      (context, message) => {
        Thread.sleep(3000)
        context.log.info(message.message)}
        println(message.message)
        Behaviors.same
    }
  }
}

object MainPrinter {

  def apply(): Behavior[String] = Behaviors.setup {
    context => {
      val printer: ActorRef[Printer.PrintMessage] = context.spawn(Printer(), "printer")
      printer ! Printer.PrintMessage("Hello from MainPrinter")
      Behaviors.same
    }
  }
}

//#greetings-main
object GreetingsMain {

  final case class SayHello(name: String)

  def apply(): Behavior[SayHello] =
    Behaviors.setup { context =>
      val greeter = context.spawn(Greetings(), "greeter")

      Behaviors.receiveMessage { message =>
        val replyTo = context.spawn(GreetingsBot(max = 3), message.name)
        greeter ! Greetings.Greet(message.name, replyTo)
        Behaviors.same
      }
    }

  def main(args: Array[String]): Unit = {
    // #greetings
    val system: ActorSystem[GreetingsMain.SayHello] =
      ActorSystem(GreetingsMain(), "hello")

    val printer: ActorSystem[String] = ActorSystem(MainPrinter(), "printer")

    printer ! "Hello from MainPrinter"

    system ! GreetingsMain.SayHello("World")
    system ! GreetingsMain.SayHello("Akka")
    // #greetings

    Thread.sleep(3000)
    system.terminate()
    printer.terminate()
  }
}
