package rxvl.oo

object model {
  sealed trait Command
  case class NoOp(id: String) extends Command
  case class Candidate(id: Int)
  case class Term(id: Int) {
    def lt(other: Term): Boolean = id < other.id

  }

  case class LogIndex(id: Int) {
    def gt(other: LogIndex): Boolean = id > other.id
    def min(other: LogIndex): LogIndex = if (id < other.id) this else other
    def inc: LogIndex = copy(id = id + 1)
  }
  case class LogEntry(term: Term, command: Command)
  case class Log(entries: Vector[LogEntry]) {
    def lastIndex = LogIndex(entries.length - 1)
    def replaceAfter(logIndex: LogIndex, newLog: Seq[LogEntry]): Log =
      copy(entries = entries.take(logIndex.id + 1) ++ newLog)
    def after(logIndex: LogIndex): Vector[LogEntry] = entries.drop(logIndex.id)
    def entryAt(logIndex: LogIndex) =  entries.lift(logIndex.id)
  }
  case class VotedFor(candidate: Option[Candidate])
  case class LogIndexPerServer(logIndexPerServer: Map[Int, LogIndex])
}

object modelJson {
  import model._
  import io.circe.generic.semiauto._
  import io.circe._

  implicit val commandDecoder: Decoder[Command] = deriveDecoder[Command]
  implicit val commandEncoder: Encoder[Command] = deriveEncoder[Command]

  implicit val termDecoder: Decoder[Term] = deriveDecoder[Term]
  implicit val termEncoder: Encoder[Term] = deriveEncoder[Term]

  implicit val candidateDecoder: Decoder[Candidate] = deriveDecoder[Candidate]
  implicit val candidateEncoder: Encoder[Candidate] = deriveEncoder[Candidate]

  implicit val logIndexDecoder: Decoder[LogIndex] = deriveDecoder[LogIndex]
  implicit val logIndexEncoder: Encoder[LogIndex] = deriveEncoder[LogIndex]

  implicit val logEntryDecoder: Decoder[LogEntry] = deriveDecoder[LogEntry]
  implicit val logEntryEncoder: Encoder[LogEntry] = deriveEncoder[LogEntry]

  implicit val logDecoder: Decoder[Log] = deriveDecoder[Log]
  implicit val logEncoder: Encoder[Log] = deriveEncoder[Log]

  implicit val votedForDecoder: Decoder[VotedFor] = deriveDecoder[VotedFor]
  implicit val votedForEncoder: Encoder[VotedFor] = deriveEncoder[VotedFor]

  implicit val LogIndexPerServerDecoder: Decoder[LogIndexPerServer] = deriveDecoder[LogIndexPerServer]
  implicit val LogIndexPerServerEncoder: Encoder[LogIndexPerServer] = deriveEncoder[LogIndexPerServer]
}


// Persistent state on all servers
// Updated on stable storage before responding to RPCs
class AllServerState {
  // Persistent State
  import state._
  import persistence._
  import volatile._
  import model._
  import modelJson._

  private val persistentState = new PersistentState

  // latest term server has seen (initialized to 0 on first boot, increases monotonically)
  val currentTerm = new JsonState[Term](persistentState, "CurrentTerm", Term(0))

  // candidateId that received vote in current term (or null if none)
  val votedFor = new JsonState[VotedFor](persistentState, "VotedFor", VotedFor(None))

  // log entries; each entry contains command for state machine, and term when entry was received by leader (first index is 1)
  val log = new JsonState[Log](persistentState, "Log", Log(Vector()))

  // Volatile State
  protected val volatileState = new VolatileState
  // index of highest log entry known to be committed (initialized to 0, increases monotonically)
  val commitIndex = new JsonState[LogIndex](volatileState, "CommitIndex", LogIndex(0))

  // index of highest log entry applied to state machine (initialized to 0, increases monotonically)
  val lastApplied = new JsonState[LogIndex](volatileState, "LastApplied", LogIndex(0))
}

class LeaderServerState extends AllServerState {
  import state._
  import model._
  import modelJson._

  // for each server, index of the next log entry to send to that server (initialized to leader last log index + 1)
  val nextIndex = new JsonState[LogIndexPerServer](volatileState, "NextIndex", LogIndexPerServer(Map()))

  // for each server, index of highest log entry known to be replicated on server(initialized to 0, increases monotonically)
  val matchIndex = new JsonState[LogIndexPerServer](volatileState, "MatchIndex", LogIndexPerServer(Map()))
}

object HandleAppendEntries {
  import model._
  import modelJson._

  case class Arguments(
    term: Term, // leader's term
    leaderId: Int, // so follower can redirect clients
    prevLogIndex: LogIndex, // index of log entry immediately preceding new ones
    prevLogTerm: Term, // term of prevLogIndex entry
    entries: Vector[LogEntry], // log entries to store (empty for heartbeat; may send more than one for efficiency)
    leaderCommit: LogIndex // leader's commitIndex
  )

  sealed trait Results
  case object OldLeader extends Results
  case class LogsDontMatch(logIndex: LogIndex, onFollower: Term, fromRPC: Term) extends Results

  case class Success(
    term: Term, // currentTerm, for leader to update itself
    newCommitIndex: LogIndex,
    newLastIndex: LogIndex
  ) extends Results

  def apply(args: Arguments, followerServerState: FollowerServerState): Results = {
    val currentTerm = followerServerState.currentTerm.get.get
    val log = followerServerState.log.get.get
    val commitIndex = followerServerState.commitIndex.get.get
    // Reply false if term < currentTerm
    if (args.term lt currentTerm) OldLeader
    // Reply false if log doesn't contain an entry with term == prevLogTerm at prevLogIndex
    else {
      val identifyingLogEntry = log.entryAt(args.prevLogIndex)
      if (!identifyingLogEntry.exists(_.term == args.prevLogTerm)) LogsDontMatch(args.prevLogIndex, identifyingLogEntry.get.term, args.prevLogTerm)
      else {
        val newLog = zipOpt(log.after(args.prevLogIndex), args.entries).map {
          case (_, Some(fromArg)) ⇒ fromArg
          case (Some(fromLog), None) ⇒ fromLog
          case (None, None) ⇒ ??? // shouldn't happen
        }
        followerServerState.log.update(_.replaceAfter(args.prevLogIndex, newLog))
        val newLastIndex = followerServerState.log.get.get.lastIndex
        val newCommitIndex = args.leaderCommit.min(newLastIndex)
        if (args.leaderCommit gt commitIndex)
          followerServerState.commitIndex.set(newCommitIndex)
        Success(currentTerm, newCommitIndex, newLastIndex)
      }
    }
  }

  private def zipOpt[A, B](s1: Seq[A], s2: Seq[B]): Seq[(Option[A], Option[B])] =
    s1.map(Option(_)).zipAll(s2.map(Option(_)), None, None)
}

class FollowerServerState extends AllServerState

class Cluster {
  val server1 = new LeaderServerState()
  val server2 = new FollowerServerState()
  val server3 = new FollowerServerState()
}



object state {
  trait State {
    def write(key: String, value: String): Unit
    def update(key: String, update: String ⇒ String): Unit
    def read(key: String): Option[String]
    def clear(): Unit
  }

  class ConcurrentHashMapStateImpl extends State {
    import java.util.concurrent.ConcurrentHashMap
    private val state = new ConcurrentHashMap[String, String]()
    def write(filename: String, contents: String): Unit = state.put(filename, contents)
    def update(filename: String, update: String ⇒ String): Unit =
      state.computeIfPresent(filename, (_: String, u: String) => update(u))
    def read(filename: String): Option[String] = Option(state.get(filename))
    def clear(): Unit = state.clear()
  }

  import io.circe._
  class JsonState[A: Encoder: Decoder](state: State, fileName: String, initValue: A) {
    import io.circe.syntax._
    import io.circe.parser._
    def get: Option[A] = state.read(fileName).flatMap(parse(_).toOption).flatMap(_.as[A].toOption)
    def set(newA: A): Unit = state.write(fileName, newA.asJson.toString())
    def update(updateA: A ⇒ A): Unit = state.update(fileName,
      in ⇒ parse(in).toOption.flatMap(_.as[A].toOption).map(updateA).map(_.asJson.toString()).getOrElse(in)
    )
    def clear(): Unit = state.clear()

    // init:
    set(initValue)
  }
}

object persistence {
  class PersistentState extends state.ConcurrentHashMapStateImpl
}

object volatile {
  class VolatileState extends state.ConcurrentHashMapStateImpl
}