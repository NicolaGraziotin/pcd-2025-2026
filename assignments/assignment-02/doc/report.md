# *Assignment-02 Report - [FSStat]*

## *Programmazione Concorrente e Distribuita [25-26]*

**Autore:** Nicola Graziotin (nicola.graziotin@studio.unibo.it)

---

## Indice
1. [Analisi del Problema](#1-analisi-del-problema)
2. [Design e Implementazione](#2-design-e-implementazione)
3. [Punto Opzionale e Limiti](#3-punto-opzionale-e-limiti)

---

## 1. Analisi del Problema

L'obiettivo dell'assignment è progettare una libreria FSStatLib capace di produrre asincronamente un report sulle dimensioni dei file contenuti in una directory, includendo ricorsivamente tutte le sottodirectory. Il problema è apparentemente semplice: visitare un albero, leggere la dimensione dei file e aggiornare dei contatori. Dal punto di vista concorrente diventa interessante perché la visita del file system è un'attività fortemente I/O-bound, con profondità e ampiezza non note a priori, e ogni nodo può essere letto indipendentemente dagli altri.

### Asincronia e ricorsione

La chiamata alla libreria non deve congelare il chiamante mentre l'albero viene esplorato: nel caso della GUI Swing questo bloccherebbe l'Event Dispatch Thread, cioè l'intera interfaccia. La difficoltà non è solo "spostare il lavoro su un thread": la scansione ricorsiva permette a ogni sottodirectory di essere esplorata indipendentemente, quindi il design deve lasciare spazio al parallelismo senza introdurre una struttura globale condivisa e contesa tra tutti i task.

Il risultato prodotto è una **fotografia** dello stato del file system al momento della visita: non c'è alcuna garanzia di coerenza rispetto a modifiche concorrenti operate da altri processi (file che compaiono o spariscono durante la scansione). Questo è un limite intrinseco del problema, non dell'implementazione.

### Composizione dei risultati

Indipendentemente dall'approccio, le tre versioni devono aggregare i contributi di ogni file in:
- il numero totale di file regolari incontrati;
- il numero di file per ciascuna delle NB + 1 fasce di dimensione: le prime NB dividono [0, MaxFS] in intervalli di ampiezza MaxFS / NB, l'ultima raccoglie i file con dimensione maggiore di MaxFS.

Il calcolo dell'indice di fascia è condiviso ed è l'unico punto in cui i contributi vengono interpretati:

```java
if (size < 0) size = 0;
if (size > maxFS) return nb;
int idx = (int) Math.min((size * (double) nb) / (double) maxFS, nb - 1);
```

La moltiplicazione in double evita l'overflow di size * nb su file molto grandi; maxFS e NB sono validati all'ingresso della libreria (NB > 0, MaxFS > 0, directory non nulla).

### Gestione delle eccezioni e directory non leggibili

Una directory non leggibile, un file rimosso tra la lettura della directory e la lettura delle sue proprietà, o un errore di permessi non devono far fallire l'intero report: il sottoalbero viene semplicemente ignorato e il resto della scansione continua. Solo una condizione invalida sull'argomento radice (path che non è una directory) fa fallire la chiamata, perché in quel caso il risultato richiesto non ha senso.

### Aggiornamenti dinamici e backpressure

Il punto opzionale richiede la possibilità di interrompere la generazione del report e di osservarne gli aggiornamenti. Il modello reattivo si presta naturalmente alla cancellazione: la sottoscrizione produce un Disposable, e la pipeline può smettere di emettere quando il consumatore si disiscrive. La libreria verifica emitter.isCancelled() durante l'attraversamento, quindi lo stop non richiede flag condivisi né thread di controllo.

Il secondo aspetto (aggiornamenti progressivi) mette in evidenza il disaccoppiamento tra produttore e consumatore: la scansione può produrre eventi molto più velocemente di quanto Swing possa ridisegnare la GUI, quindi serve una politica esplicita. La libreria reattiva espone per questo un Flowable\<FSReport> costruito con scanWith (report parziale dopo ogni file, con lo stato iniziale fornito da una factory) e sample(100 ms, emitLast = true) prima della GUI: il consumatore riceve al massimo uno snapshot ogni 100 ms e sempre l'ultimo, senza accumulare eventi arretrati. Il Single\<FSReport> resta invece l'API per i client non interattivi (console).

---

## 2. Design e Implementazione

Le tre versioni condividono solo il tipo FSReport, che è una struttura dati immutabile e non un'astrazione di programmazione: riusarla non nasconde le differenze di design tra event-loop, programmazione reattiva e virtual threads, che restano tre implementazioni indipendenti. Non è stata introdotta un'interfaccia comune né una classe base, proprio per non omogeneizzare i tre approcci.

### Struttura del report

FSReport è immutabile: contiene la directory radice, MaxFS, NB, il numero totale di file e un vettore long[] bands di NB + 1 elementi (viene copiato alla costruzione). Il metodo statico bandIndex mappa una dimensione sulla fascia; toString materializza i range in forma leggibile (es. [0, 250), con l'ultima fascia (MaxFS, +inf)) e viene usato sia dalla console sia dalla GUI. Non ci sono contatori modificabili né un metodo merge: l'aggregazione avviene dentro ciascuna versione secondo il proprio modello, e il report finale viene creato una sola volta, alla fine, da un array di contatori locale alla scansione. L'immutabilità serve anche alla versione live, dove ogni snapshot è un oggetto distinto: gli aggiornamenti successivi non modificano i report già consegnati alla GUI.

Il numero totale di file è ridondante rispetto alla somma delle fasce, ma è esposto esplicitamente perché è un requisito e perché evita al client di dover sommare le fasce.

### Event-loop: Vert.x

La libreria restituisce un Future\<FSReport> e tutta la coordinazione vive sul thread dell'event-loop di Vert.x. L'attraversamento è un albero di callback asincrone: fs.props verifica che la radice sia una directory, fs.readDir elenca i figli, e per ogni figlio fs.props decide se ricorrere (visitDir) o contare il file (countFile).

Lo stato della scansione (bands, totalFiles) è mutato **solo** sul thread dell'event-loop, quindi non servono lock: il parallelismo nasce dal numero di operazioni di I/O asincrone contemporaneamente in volo, non da thread concorrenti. L'unico contatore atomico è pendingDirs, che tiene traccia delle directory ancora aperte: ogni visitDir lo incrementa, ogni directory completata (anche per errore) lo decrementa, e quando arriva a zero la Promise viene completata con lo snapshot del report.

Il codice non chiama mai join, get o sleep bloccanti. Gli errori di accesso a un singolo nodo vengono assorbiti: una readDir fallita produce semplicemente un sottoalbero vuoto, mentre un errore sulla radice fa fallire il Future. I flag settled e promise.tryComplete/tryFail rendono il completamento idempotente.

### Programmazione reattiva: RxJava

La libreria restituisce uno Single\<FSReport> per l'uso non interattivo, costruito per composizione di operatori:

1. listFilesRecursive crea un Flowable\<Path> con un attraversamento iterativo in profondità (stack esplicito, DirectoryStream, nessuna ricorsione sullo stack del thread). Il Flowable è creato con BackpressureStrategy.BUFFER e sottoscritto su Schedulers.io().
2. flatMapSingle trasforma ogni path nella sua dimensione tramite Files.size, eseguito su Schedulers.io(): ogni file è un task indipendente, quindi il parallelismo è delegato allo scheduler.
3. onErrorReturnItem(-1) + filter scartano i file non più leggibili, realizzando la tolleranza agli errori senza interrompere il flusso.
4. collect accumula in un Accumulator locale (array di fasce + totale) e map produce lo snapshot immutabile.

I passi 1–3 sono condivisi con getFSReportUpdates, l'API live usata dalla GUI, che al posto di collect usa:

5. scanWith con un Accumulator come stato: dopo ogni dimensione emette il report parziale corrente.
6. map verso FSReport (che copia il vettore delle fasce, quindi le istantanee già emesse non cambiano) e sample(UPDATE_INTERVAL_MS, MILLISECONDS, true), che scarta gli aggiornamenti intermedi e tiene solo l'ultimo di ogni finestra, emettendo comunque l'ultimo alla fine.

Lo scanWith muta lo stesso accumulatore a ogni elemento, ma è sicuro perché Rx serializza le chiamate onNext; l'immutabilità di FSReport evita che l'aggiornamento successivo modifichi uno snapshot già consegnato al client.

Non ci sono thread, lock o contatori espliciti: la concorrenza è dichiarata con subscribeOn/flatMapSingle, e la mutazione dell'accumulatore è sicura perché Rx serializza le chiamate onNext verso un singolo observer. La cancellazione è cooperativa: il ciclo di attraversamento controlla emitter.isCancelled() a ogni directory e a ogni entry, quindi dispose() interrompe la produzione senza flag condivisi.

#### GUI Swing

GuiRx fornisce tre campi (directory, MaxFS, NB) e i pulsanti Start/Stop. Start si sottoscrive a getFSReportUpdates, aggiornando l'area di testo a ogni report parziale ricevuto; Stop chiama dispose() sul Disposable corrente. Poiché i callback Rx arrivano su thread di Schedulers.io()/computation(), ogni aggiornamento della GUI passa da SwingUtilities.invokeLater, rispettando il confine dell'EDT, e un aggiornamento già accodato viene ignorato se nel frattempo la sottoscrizione è stata disposta. La disiscrizione è anche il meccanismo con cui la GUI segnala l'annullamento: non serve un flag letto dalla scansione.

### Virtual threads

La libreria restituisce un CompletableFuture\<FSReport> e usa Executors.newVirtualThreadPerTaskExecutor(): la chiamata pubblica è asincrona, il client ottiene subito il future mentre la visita avviene su virtual thread.

Per ogni sottodirectory viene sottomesso un task sullo stesso executor; il task padre attende i figli con Future.get() e al termine costruisce lo snapshot. In una soluzione con platform thread questa forma rischierebbe di saturare il pool (thread genitori bloccati in attesa dei figli); con i virtual thread l'attesa parcheggia il thread virtuale e il carrier thread viene riutilizzato dalla JVM, quindi non c'è né pool exhaustion né deadlock indotto dal pool.

A differenza delle altre due versioni, qui i contatori sono condivisi tra i task: total è un LongAdder e ogni fascia è un LongAdder. LongAdder è lock-free e pensato per molti scrittori concorrenti, e la somma finale viene copiata in FSReport solo dopo che tutti i figli sono terminati. Non si usa synchronized, che oltretutto rischierebbe di fare pinning del virtual thread sul carrier. La libreria implementa AutoCloseable: close() chiama shutdownNow(), e la scansione controlla Thread.currentThread().isInterrupted() per fermarsi in modo cooperativo.

---

## 3. Punto Opzionale

Il punto opzionale è coperto dalla versione reattiva:

- **Stop**: implementato nella GUI tramite Disposable.dispose(), che interrompe la produzione perché la libreria controlla emitter.isCancelled(); nella versione a virtual thread è presente l'interruzione dell'executor con controllo del flag di interruzione, senza GUI.
- **Aggiornamenti dinamici**: la GUI si sottoscrive a getFSReportUpdates, che emette report parziali tramite scanWith e li limita con sample(100 ms, emitLast = true) prima del rendering. Il consumatore vede quindi la statistica crescere durante la scansione, ma al massimo 10 aggiornamenti al secondo: la backpressure non si accumula e l'EDT non viene saturato. L'emitLast garantisce che il report completo venga sempre mostrato anche se l'ultima finestra di campionamento non è scaduta.
