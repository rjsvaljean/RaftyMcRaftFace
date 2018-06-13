package rxvl.oo

import rxvl.oo.HandleRequestVote._
import rxvl.oo.model._
import rxvl.oo.model.requestVote._

class HandleRequestVoteRPCSpec extends RaftSpec {
  behavior of HandleRequestVote.getClass.getSimpleName

  it should "grant vote if not yet voted and candidate's log exceeds the server's" in {
    val startLogState = Log(Vector(
      1 → cmd("a"),
      2 → cmd("b")
    ))
    val serverState = createFollowerState(log = startLogState, votedFor = VotedFor(None))
    val argument = Arguments(term = 3, candidateId = 1, lastLogIndex = 2, lastLogTerm = 3)

    HandleRequestVote(argument, serverState) must be(VoteGranted)
  }

  it should "grant vote if already voted for the candidate and candidate's log exceeds the server's" in {
    val startLogState = Log(Vector(
      1 → cmd("a"),
      2 → cmd("b")
    ))
    val serverState = createFollowerState(log = startLogState, votedFor = VotedFor(Some(1)))
    val argument = Arguments(term = 3, candidateId = 1, lastLogIndex = 2, lastLogTerm = 3)

    HandleRequestVote(argument, serverState) must be(VoteGranted)
  }

  it should "not grant vote if already voted for another candidate" in {
    val serverState = createFollowerState(VotedFor(Some(2)))
    val argument = Arguments(term = 3, candidateId = 1, lastLogIndex = 2, lastLogTerm = 3)

    HandleRequestVote(argument, serverState) must be(VoteNotGranted(
      notYetVotedOrAlreadyVotedForThisCandidate = false,
      candidatesLogIsAtLeastAsUpToDateAsReceiversLog = true
    ))
  }

  it should "not grant vote if candidate's log is not up to date to the server's" in {
    val startLogState = Log(Vector(
      1 → cmd("a"),
      2 → cmd("b")
    ))
    val serverState = createFollowerState(startLogState, VotedFor(None))
    val argument = Arguments(term = 3, candidateId = 1, lastLogIndex = 0, lastLogTerm = 3)

    HandleRequestVote(argument, serverState) must be(VoteNotGranted(
      notYetVotedOrAlreadyVotedForThisCandidate = true,
      candidatesLogIsAtLeastAsUpToDateAsReceiversLog = false
    ))
  }
}
