package main

import (
	"fmt"
	"math/rand/v2"
)

type MatchResult struct {
	Index     int
	MatchID   string
	Round     int
	OddsID    int
	EvensID   int
	OddsMove  int
	EvensMove int
	Sum       int
	Winner    int
	Loser     int
}

func PlayMatch(round, index int, a, b PlayerHandle, resultCh chan<- MatchResult, logCh chan<- string, seed int64) {
	matchID := fmt.Sprintf("R%d-M%d", round, index)
	rng := rand.New(rand.NewPCG(uint64(seed), uint64(index)))

	odds, evens := a, b
	if rng.IntN(2) == 1 {
		odds, evens = b, a
	}

	replyOdds := make(chan PlayerResponse, 1)
	replyEvens := make(chan PlayerResponse, 1)

	odds.Inbox <- PlayerMsg{Kind: MsgPlay, MatchID: matchID, Role: Odds, ReplyCh: replyOdds}
	evens.Inbox <- PlayerMsg{Kind: MsgPlay, MatchID: matchID, Role: Evens, ReplyCh: replyEvens}

	respOdds := <-replyOdds
	respEvens := <-replyEvens

	winner, sum := DecideWinner(odds.ID, evens.ID, respOdds.Move, respEvens.Move)
	loser := evens.ID
	if winner == odds.ID {
		loser = evens.ID
	} else {
		loser = odds.ID
	}

	parity := "ODD"
	if sum%2 == 0 {
		parity = "EVEN"
	}
	emit(logCh, fmt.Sprintf("[round %d][%s] P%d (Odds, shows %d) vs P%d (Evens, shows %d) -> sum %d %s -> P%d wins",
		round, matchID, odds.ID, respOdds.Move, evens.ID, respEvens.Move, sum, parity, winner))

	resultCh <- MatchResult{
		Index:     index,
		MatchID:   matchID,
		Round:     round,
		OddsID:    odds.ID,
		EvensID:   evens.ID,
		OddsMove:  respOdds.Move,
		EvensMove: respEvens.Move,
		Sum:       sum,
		Winner:    winner,
		Loser:     loser,
	}
}
