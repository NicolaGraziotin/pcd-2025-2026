package main

import (
	"fmt"
	"sort"
)

type Config struct {
	NumPlayers  int
	NumRounds   int
	Seed        int64
	ThinkTimeMs int
}

func RunTournament(cfg Config, logCh chan<- string) (champion int, ordered []MatchResult) {
	handles := spawnPlayers(cfg.NumPlayers, cfg.Seed, cfg.ThinkTimeMs, logCh)
	byID := make(map[int]PlayerHandle, len(handles))
	for _, h := range handles {
		byID[h.ID] = h
	}

	current := handles
	ordered = make([]MatchResult, 0, cfg.NumPlayers-1)

	for round := 1; round <= cfg.NumRounds; round++ {
		numMatches := len(current) / 2
		emit(logCh, fmt.Sprintf("[round %d] starting %d match(es) with %d players", round, numMatches, len(current)))

		resultCh := make(chan MatchResult, numMatches)
		for i := 0; i < len(current); i += 2 {
			idx := i / 2
			a, b := current[i], current[i+1]
			go PlayMatch(round, idx, a, b, resultCh, logCh, cfg.Seed+int64(round))
		}

		roundResults := make([]MatchResult, 0, numMatches)
		for k := 0; k < numMatches; k++ {
			roundResults = append(roundResults, <-resultCh)
		}

		sort.Slice(roundResults, func(i, j int) bool { return roundResults[i].Index < roundResults[j].Index })
		ordered = append(ordered, roundResults...)

		next := make([]PlayerHandle, 0, numMatches)
		for _, res := range roundResults {
			next = append(next, byID[res.Winner])
		}
		emit(logCh, fmt.Sprintf("[round %d] winners: %v", round, winnerIDs(roundResults)))
		current = next
	}

	champion = current[0].ID
	emit(logCh, fmt.Sprintf("[done] champion: P%d (after %d matches)", champion, len(ordered)))

	for _, h := range handles {
		h.Inbox <- PlayerMsg{Kind: MsgQuit}
	}
	return champion, ordered
}

func winnerIDs(results []MatchResult) []int {
	ids := make([]int, len(results))
	for i, r := range results {
		ids[i] = r.Winner
	}
	return ids
}
