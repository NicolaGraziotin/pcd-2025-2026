# Odds and Evens Game

## 1. Run the program:

To run the program, use the following commands:

```bash
cd odds_evens_game/src

go run .
```

### Flags

Optional command-line arguments to customize the simulation behavior.

|Flag|Default|Description|
|-|-|-|
|`-players`|`0`|Specify the number of players (optional).|
|`-rounds`|`3`|Specify the number of rounds to play (optional). Used when `-players` is not specified.|
|`-seed`|none|Specify a seed for the random number generator (optional).|
|`-think-ms`|`20`|Specify the thinking time in milliseconds for each player (optional).|

### Example

Run a 4-player game with a fixed seed and custom thinking time.

```bash
go run . -players 4 -rounds 5 -seed 42 -think-ms 50
```