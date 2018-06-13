package rxvl

import java.nio.file.Paths

import cats.Show
import cats.syntax.show._
import cats.instances.string._
import cats.effect.IO
import fs2.StreamApp.ExitCode
import fs2.text.{lines, utf8Decode}
import fs2.{Pipe, Stream, StreamApp}

object Sim {
  import io.circe.generic.semiauto._
  import io.circe._

  sealed trait Command
  case class AddServer(id: Int) extends Command
  case object Tick extends Command
  case class AppendEntries(id: Int, cmd: oo.model.appendEntries.Arguments) extends Command
  case class RequestVote(id: Int, cmd: oo.model.requestVote.Arguments) extends Command
  object Command {
    import oo.modelJson._
    implicit val commandDecoder: Decoder[Command] = deriveDecoder[Command]
    implicit val commandEncoder: Encoder[Command] = deriveEncoder[Command]
    implicit val show: Show[Command] = Show.fromToString[Command]
  }
}

object Main extends StreamApp[cats.effect.IO] {

  def stream(args: List[String], requestShutdown: IO[Unit]): Stream[IO, ExitCode] =
    (
      if (args.headOption.contains("template")) printTemplate
      else if (args.lift(0).contains("-f")) readFromFileAndProcess(args.lift(1).get)
      else readFromStdInAndProcess
    )
      .last
      .map(_ ⇒ ExitCode.Success)

  def printLine[T: Show]: Pipe[IO, T, T] = _.evalMap(t ⇒ IO(println(t.show)).map(_ ⇒ t))

  val deserialize: String ⇒ IO[Sim.Command] = {
    import io.circe._
    import io.circe.parser.parse
    val jsonToCommand: Json ⇒ IO[Sim.Command] = ((_: Json).as[Sim.Command]) andThen IO.fromEither[Sim.Command]
    parse _ andThen IO.fromEither andThen ((_: IO[Json]).flatMap(jsonToCommand))
  }
  val serialize: Sim.Command ⇒ String = {
    import io.circe.syntax._
    (_: Sim.Command).asJson.noSpaces
  }
  def printTemplate: Stream[IO, String] = {
    import oo.model._
    Stream
      .emits(Seq(
        Sim.AddServer(1),
        Sim.Tick,
        Sim.AppendEntries(1, oo.model.appendEntries.Arguments(
          term = Term(1),
          leaderId = 1,
          prevLogIndex = LogIndex(1),
          prevLogTerm = Term(1),
          entries = Vector(
            LogEntry(Term(1), NoOp("a"))
          ),
          leaderCommit = LogIndex(1)
        )),
        Sim.RequestVote(1, oo.model.requestVote.Arguments(
          term = Term(1),
          candidateId = Candidate(1),
          lastLogIndex = LogIndex(1),
          lastLogTerm = Term(1)
        ))
      ))
      .map(serialize)
      .evalMap(IO(_))
      .through(printLine[String])
  }

  def toLines(bytes: Stream[IO, Byte]): Stream[IO, String] =
    bytes.through(utf8Decode[IO]).through(lines[IO]).filter(_.nonEmpty)

  val stdInLines: Stream[IO, String] = toLines(fs2.io.stdin[IO](10))
  def readFromFile(file: String): Stream[IO, String] = toLines(fs2.io.file.readAll[IO](Paths.get(file), 10))


  def readFromFileAndProcess(file: String): Stream[IO, Sim.Command] = andProcess(readFromFile(file))
  def readFromStdInAndProcess: Stream[IO, Sim.Command] = andProcess(stdInLines)

  def andProcess(input: Stream[IO, String]): Stream[IO, Sim.Command] =
    input
      .through(printLine[String])
      .evalMap(deserialize)
      .through(printLine[Sim.Command])
}
