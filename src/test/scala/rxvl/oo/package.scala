package rxvl

import org.scalatest.{AppendedClues, FlatSpec, MustMatchers}
import rxvl.oo.model._

package object oo {
  trait RaftSpec extends FlatSpec with MustMatchers with AppendedClues with RaftFollowerCreationHelpers

  trait RaftModelHelpers {
    import scala.language.implicitConversions

    implicit def intToLogIndex(i: Int): LogIndex = LogIndex(i)
    implicit def intToCandidate(i: Int): Candidate = Candidate(i)
    implicit def intToTerm(i: Int): Term = Term(i)
    implicit def pairToLogEntry(pair: (Int, Command)): LogEntry = LogEntry(pair._1, pair._2)
    def cmd(index: String): Command = NoOp(index)
  }

  trait RaftFollowerCreationHelpers extends RaftModelHelpers {
    protected def createFollowerState(log: Log): FollowerServerState = {
      val (LogEntry(term, _), lastIndex) = log.entries.zipWithIndex.last
      createFollowerState(lastIndex, lastIndex, term, log)
    }

    protected def createFollowerState(log: Log, votedFor: VotedFor): FollowerServerState = {
      val serverState = createFollowerState(log)
      serverState.votedFor.set(votedFor)
      serverState
    }

    protected def createFollowerState(votedFor: VotedFor): FollowerServerState = {
      val serverState = createFollowerState(Log(Vector(1 → cmd("a"))))
      serverState.votedFor.set(votedFor)
      serverState
    }

    protected def createFollowerState(commitIndex: LogIndex, lastApplied: LogIndex, currentTerm: Term, log: Log): FollowerServerState = {
      val serverState = new FollowerServerState
      serverState.commitIndex.set(commitIndex)
      serverState.lastApplied.set(lastApplied)
      serverState.log.set(log) // Follower has applied and committed the first 4 log entries as well
      serverState.currentTerm.set(currentTerm)
      serverState
    }

  }
}
