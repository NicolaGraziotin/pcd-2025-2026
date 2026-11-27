package main

import (
	"fmt"
	"math/rand/v2"
	"time"
)

type MsgKind int

const (
	MsgPlay MsgKind = iota
	MsgQuit
)

type PlayerMsg struct {
	Kind    MsgKind
	MatchID string
	Role    Role
	ReplyCh chan<- PlayerResponse
}

type PlayerResponse struct {
	PlayerID int
	MatchID  string
	Role     Role
	Move     int
}

type PlayerHandle struct {
	ID    int
	Inbox chan<- PlayerMsg
}

func RunPlayer(id int, inbox <-chan PlayerMsg, logCh chan<- string, seed int64, thinkTimeMs int) {
	rng := rand.New(rand.NewPCG(uint64(seed), uint64(id)))
	for msg := range inbox {
		switch msg.Kind {
		case MsgQuit:
			return
		case MsgPlay:
			if thinkTimeMs > 0 {
				time.Sleep(time.Duration(rng.IntN(thinkTimeMs)) * time.Millisecond)
			}
			move := MinHand + rng.IntN(MaxHand-MinHand+1)
			msg.ReplyCh <- PlayerResponse{
				PlayerID: id,
				MatchID:  msg.MatchID,
				Role:     msg.Role,
				Move:     move,
			}
		}
	}
}

func spawnPlayers(n int, seed int64, thinkTimeMs int, logCh chan<- string) []PlayerHandle {
	handles := make([]PlayerHandle, n)
	for i := 0; i < n; i++ {
		inbox := make(chan PlayerMsg, 4)
		handles[i] = PlayerHandle{ID: i, Inbox: inbox}
		go RunPlayer(i, inbox, logCh, seed+int64(i), thinkTimeMs)
	}
	emit(logCh, fmt.Sprintf("[setup] spawned %d player processes", n))
	return handles
}
