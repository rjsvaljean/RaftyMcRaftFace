package rxvl.oo

import org.scalatest.{AppendedClues, FlatSpec, MustMatchers}
import rxvl.oo.HandleAppendEntries.{Arguments, Results, Success}
import rxvl.oo.model._
import ai.x.diff.DiffShow
import ai.x.diff.conversions._

class AppendEntriesRPCSpec extends FlatSpec with MustMatchers with AppendedClues {
  behavior of HandleAppendEntries.getClass.getSimpleName

  implicit def intToLogIndex(i: Int): LogIndex = LogIndex(i)
  implicit def intToTerm(i: Int): Term = Term(i)
  implicit def pairToLogEntry(pair: (Int, Command)): LogEntry = LogEntry(pair._1, pair._2)
  def cmd(index: String): Command = NoOp(index)

  it should "append entries in the normal case" in {
    val startLogState = Log(Vector(
      1 → cmd("a"),
      2 → cmd("b"),
      2 → cmd("c"),
      3 → cmd("d")
    ))
    val serverState = createFollowerState(
      commitIndex = 3,
      lastApplied = 3,
      currentTerm = 3,
      log = startLogState)
    val argument = Arguments(
      term = 3,
      leaderId = 1,
      prevLogIndex = 3,
      prevLogTerm = 3,
      entries = Vector(
        3 → cmd("e"),
        3 → cmd("f"),
        3 → cmd("g")
      ),
      leaderCommit = 6
    ) // Leader has commited the first 4 entries [1a, 2b, 2c, 3d] and is now sending the next 3: [3e, 3f, 3g]

    HandleAppendEntries(argument, serverState) must be(Success(3, 6, 6))
    val updatedLog = serverState.log.get.get
    val expectedLog = Log(startLogState.entries ++ argument.entries)
    updatedLog must be(expectedLog) withClue DiffShow[Log].diff(updatedLog, expectedLog)
    serverState.commitIndex.get.get.id must be(6)
  }

  private def createFollowerState(commitIndex: LogIndex, lastApplied: LogIndex, currentTerm: Term, log: Log) = {
    val serverState = new FollowerServerState
    serverState.commitIndex.set(commitIndex)
    serverState.lastApplied.set(lastApplied)
    serverState.log.set(log) // Follower has applied and committed the first 4 log entries as well
    serverState.currentTerm.set(currentTerm)
    serverState
  }
}