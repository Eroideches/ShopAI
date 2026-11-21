# ShopAI

App Android personale per confrontare i prezzi. Scrivi il nome di un prodotto (o scansiona il codice a barre)
e ShopAI cerca in parallelo sui siti più noti in Italia. Per ogni sito tiene al massimo **3 risultati, i più pertinenti e tra questi i più economici**, poi li ordina per **prezzo totale crescente**, spedizione inclusa.

- **Prezzi sempre in €** come li mostra il sito: un prezzo in un'altra valuta viene scartato, mai convertito.
- **Spedizione** calcolata verso **20096 Pioltello (MI)**, modificabile nelle Impostazioni.
- **Nessun server**: tutto gira sul telefono.

## Siti
Amazon, eBay, AliExpress, Temu, Shein, TikTok Shop *(sperimentale)*, MediaWorld, Unieuro, Euronics, ePRICE, Back Market,
Leroy Merlin, ManoMano, IKEA, Decathlon, Zalando, Trovaprezzi, Idealo, e per l'usato Subito e Vinted (spenti di default).

## Funzioni
- **Schede risultato**: immagine a sinistra con il tag del sito, a destra prezzo, spedizione, descrizione del sito, consegna, valutazione e link "Apri".
- **Filtro di pertinenza**: niente cover, cavi o pellicole quando cerchi il prodotto. Scarta numeri e modelli diversi e prezzi sospetti, che finiscono nella sezione "Scartati" e si possono vedere.
- **Filtri**: ordinamento, nuovo o usato, prezzo massimo, consegna entro, solo spedizione gratuita, siti.
- **Salvati** con avviso di prezzo, **ricerche seguite**, **storico con grafico** e controlli in background con notifica.
- **Scanner di codici a barre** (EAN): ricava il nome del prodotto e cerca.
- **Prezzo per unità** (€/kg, €/l, €/pz), condivisione, apertura nell'app del sito se installata.
- **Chiavi API** facoltative per eBay e AliExpress, inserite nell'app e cifrate con l'Android Keystore. Ogni sito ha la sua guida passo passo con i link.
- **Diagnostica**: prova tutti i siti ed esporta un rapporto zip con le pagine lette.

## Resistenza ai cambiamenti dei siti
Ogni sito viene letto con una catena di metodi. Se uno smette di funzionare si passa da soli al successivo:
1. **API ufficiale**, se ci sono le chiavi.
2. **JSON di rete** intercettati nella WebView.
3. **Dati strutturati** (JSON-LD, `__NEXT_DATA__`, stati incorporati).
4. **Selettori** delle regole.
5. **Riconoscimento euristico** delle schede ripetute.

Le regole stanno in [`rules/sites.json`](rules/sites.json). L'app le scarica una volta al giorno da questa repo, quindi un sito che cambia grafica si ripara modificando il file, senza reinstallare l'app.

## Struttura
- `core/`: Kotlin puro, senza Android. Contiene modello, prezzi, pertinenza, estrattori, regole e API, con i test JVM.
- `app/`: app Android con Compose, Room, WorkManager, WebView, CameraX e ML Kit.

## Build
GitHub Actions compila a ogni push su `main`: esegue i test di `core`, firma l'APK e lo pubblica nella release `latest`.
In locale, senza SDK Android, si possono comunque provare i test di core:
```
gradle -p tools/core-test :core:test
```
