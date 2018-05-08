package rxvl.oo

import org.scalatest.{FlatSpec, MustMatchers}
import rxvl.oo.HandleAppendEntries.Arguments
import rxvl.oo.model._

class AppendEntriesRPCSpec extends FlatSpec with MustMatchers {
  behavior of HandleAppendEntries.getClass.getSimpleName

  implicit def intToLogIndex(i: Int): LogIndex = LogIndex(i)
  implicit def intToTerm(i: Int): Term = Term(i)
  implicit def pairToLogEntry(pair: (Int, Command)): LogEntry = LogEntry(pair._1, pair._2)
  def cmd(index: String): Command = NoOp(index)

  it should "do a thing " in {
    val argument = Arguments(
      term = 3,
      leaderId = 1,
      prevLogIndex = 3,
      prevLogTerm = 3,
      entries = Vector(
        LogEntry(3, cmd("e")),
        LogEntry(3, cmd("f")),
        LogEntry(3, cmd("g"))
      ),
      leaderCommit = 3
    ) // Leader has commited the first 4 entries [1a, 2b, 2c, 3d] and is now sending the next 3: [3e, 3f, 3g]
    val serverState = new FollowerServerState
    serverState.commitIndex.set(3)
    serverState.lastApplied.set(3)
    serverState.log.set(Log(Vector(
      1 → cmd("a"),
      2 → cmd("b"),
      2 → cmd("c"),
      3 → cmd("d")
    ))) // Follower has applied and committed the first 4 log entries as well
    serverState.currentTerm.set(3)
    HandleAppendEntries(argument, serverState)
    serverState.log.get.get.entries must be(Vector(
      1 → cmd("a"),
      2 → cmd("b"),
      2 → cmd("c"),
      3 → cmd("d"),
      3 → cmd("e"),
      3 → cmd("f"),
      3 → cmd("g")
    ))
    serverState.commitIndex.get.get.id must be(6)
  }
}