package main

const (
	MinHand = 0
	MaxHand = 5
)

type Role string

const (
	Odds  Role = "Odds"
	Evens Role = "Evens"
)

func DecideWinner(oddsID, evensID, oddsMove, evensMove int) (winnerID, sum int) {
	sum = oddsMove + evensMove
	if sum%2 == 0 {
		return evensID, sum
	}
	return oddsID, sum
}

func IsPowerOfTwo(n int) bool {
	return n >= 2 && n&(n-1) == 0
}

func NumRounds(numPlayers int) int {
	m := 0
	for n := numPlayers; n > 1; n >>= 1 {
		m++
	}
	return m
}

func emit(logCh chan<- string, msg string) {
	if logCh != nil {
		logCh <- msg
	}
}
