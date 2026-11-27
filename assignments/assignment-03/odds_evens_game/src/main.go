package main

import (
	"flag"
	"fmt"
	"os"
	"time"
)

func runLogger(logCh <-chan string, done chan<- struct{}) {
	for msg := range logCh {
		fmt.Println(msg)
	}
	close(done)
}

func main() {
	playersFlag := flag.Int("players", 0, "number of players N (must be a power of two >= 2); overrides -rounds if set")
	roundsFlag := flag.Int("rounds", 3, "number of rounds m, i.e. N = 2^m players (used when -players is not set)")
	seedFlag := flag.Int64("seed", 0, "RNG seed (default: current time in ns)")
	thinkFlag := flag.Int("think-ms", 20, "max random thinking delay per throw in milliseconds (0 disables it)")
	flag.Parse()

	numPlayers := *playersFlag
	numRounds := *roundsFlag

	if numPlayers != 0 {
		if !IsPowerOfTwo(numPlayers) {
			fmt.Fprintf(os.Stderr, "error: -players=%d is not a power of two >= 2\n", numPlayers)
			os.Exit(1)
		}
		numRounds = NumRounds(numPlayers)
		if *roundsFlag != 3 && *roundsFlag != numRounds {
			fmt.Fprintf(os.Stderr, "error: -players=%d implies m=%d but -rounds=%d was given\n", numPlayers, numRounds, *roundsFlag)
			os.Exit(1)
		}
	} else {
		if numRounds < 1 || numRounds > 12 {
			fmt.Fprintf(os.Stderr, "error: -rounds=%d out of range [1, 12]\n", numRounds)
			os.Exit(1)
		}
		numPlayers = 1 << numRounds
	}
	if *thinkFlag < 0 {
		fmt.Fprintf(os.Stderr, "error: -think-ms must be >= 0\n")
		os.Exit(1)
	}

	seed := *seedFlag
	if seed == 0 {
		seed = time.Now().UnixNano()
	}

	cfg := Config{
		NumPlayers:  numPlayers,
		NumRounds:   numRounds,
		Seed:        seed,
		ThinkTimeMs: *thinkFlag,
	}

	logCh := make(chan string, 128)
	done := make(chan struct{})
	go runLogger(logCh, done)

	fmt.Printf("Odds-and-Evens tournament: N=%d players, m=%d rounds, seed=%d\n", cfg.NumPlayers, cfg.NumRounds, cfg.Seed)
	champion, _ := RunTournament(cfg, logCh)

	close(logCh)
	<-done
	fmt.Printf("Champion: P%d\n", champion)
}
