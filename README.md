# WTP Swing Navigator

Desktopowa aplikacja Java (Swing) do planowania podróży komunikacją miejską w Warszawie.
Łączy statyczny rozkład jazdy (GTFS) z pozycjami pojazdów na żywo z API UM Warszawa
i wyświetla je na mapie OpenStreetMap.

> **Status: projekt zamknięty.** Repozytorium zostaje w obecnym stanie jako archiwum
> wykonanych prac. Nie jest dalej rozwijane.

## Co potrafi

- **Mapa** (JXMapViewer2 + OpenStreetMap): wszystkie przystanki jako punkty, kliknięcie otwiera tablicę odjazdów.
- **Pojazdy na żywo**: autobusy i tramwaje z API UM Warszawa, cykliczne odświeżanie w tle,
  ręczne odświeżenie, wskaźnik połączenia z API i tryb offline.
- **Wyszukiwarka przystanków** z podpowiedziami.
- **Tablica odjazdów** wybranego przystanku, z obliczaniem opóźnień na podstawie danych GPS.
- **Planer połączeń** (zakładka „Połączenia”): przystanek początkowy i końcowy oraz godzina wyjazdu
  (domyślnie bieżąca godzina systemowa, format `HH:mm`).
  - trasy bezpośrednie oraz z jedną przesiadką, do 20 najlepszych wyników posortowanych wg przyjazdu,
  - czas przejazdu, liczba przystanków i przesiadek, czas oczekiwania,
  - szczegółowy opis trasy odcinek po odcinku, podświetlenie trasy na mapie.
- **Dojście piesze przy przesiadkach**: szacunek liniowy, a z kluczem OpenRouteService
  (profil `foot-walking`) czas zmierzony przez ORS. Przy braku klucza lub błędzie API zostaje szacunek.
- **Wczytywanie rozkładów** z plików GTFS w tle (`SwingWorker`), interfejs FlatLaf.

## Wymagania

- JDK zgodny z `pom.xml` (obecnie `maven.compiler.source/target` = 27)
- Maven
- Dostęp do internetu (kafle mapy, API pojazdów, opcjonalnie ORS)

## Budowanie i uruchomienie

```
mvn package
run.bat          # Windows
./run.sh         # Linux/macOS
```

Skrypty uruchamiają `target/wtp-swing-navigator-0.0.2-SNAPSHOT-shaded.jar`. Opcje JVM można nadpisać
zmienną `JAVA_OPTS`. Można też uruchomić klasę `pl.j.reinmar.mapwaw.wtp.MainApp`
(opcjonalny argument: katalog z danymi GTFS, domyślnie `src/main/resources/data`).

Testy: `mvn test`. Część testów (np. `WtpRealtimeApiClientLiveTest`, `MapRenderingLoadTest`)
wymaga sieci lub jest domyślnie wyłączona.

## Konfiguracja

Plik `src/main/resources/config.properties`:

| Klucz | Opis |
|-------|------|
| `wtp.api.endpoint`, `wtp.resource.id` | API pojazdów UM Warszawa |
| `wtp.api.key` | klucz API UM Warszawa (można nadpisać plikiem `apiKey.txt`) |
| `ors.api.key` | klucz OpenRouteService (lub zmienna środowiskowa `ORS_API_KEY`); puste = ORS wyłączony |
| `ors.api.endpoint` | adres ORS, domyślnie `.../v2/directions/foot-walking` |

**Uwaga:** w repozytorium znajduje się domyślny klucz API UM Warszawa. Przed ewentualnym
upublicznieniem warto go wycofać i podać własny.

## Struktura projektu

```
src/main/java/pl/j/reinmar/mapwaw/wtp/
  MainApp.java        punkt wejścia, składanie interfejsu
  config/             AppConfig (config.properties, klucze API)
  model/              Stop, Line, RouteVariant, Departure, LiveVehiclePosition...
  parser/             GTFS i rozkłady TXT, JSON z API pojazdów, walidatory
  repository/         ScheduleRepository, RealtimeVehicleCache, cache rozkładu
  service/            RoutingEngine, WtpRealtimeApiClient, OrsWalkingService,
                      DelayCalculatorService, harmonogramy odświeżania
  ui/                 view (panele, mapa), component, controller, worker
src/main/resources/   config.properties, logback.xml, data/ (GTFS), icons/
src/test/java/        testy jednostkowe, wydajnościowe i akceptacyjne
```

## Technologie

Java, Swing, [JXMapViewer2](https://github.com/msteiger/jxmapviewer2) 2.8,
[FlatLaf](https://www.formdev.com/flatlaf/) 3.6.2, Jackson 2.17.2, SLF4J/Logback,
JUnit 5, Maven (shade plugin).

## Znane ograniczenia

- Routing obsługuje trasy bezpośrednie i z jedną przesiadką.
- Początek i koniec trasy to przystanki; brak dojścia do/z dowolnego adresu.
- Czasy z ORS nie zmieniają godzin przyjazdu ani kolejności wyników.
- Projekt nie był budowany ani testowany Mavenem w ostatniej sesji pracy
  (kod sprawdzony kompilacją `javac`).
