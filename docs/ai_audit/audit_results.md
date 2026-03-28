# Audit Systemowy: ThingSpeak Monitor Widget APK

## Wstęp
Analiza 30 kluczowych obszarów, które powodują "gubienie się" agentów AI, powtarzalne błędy w renderowaniu dashboardu oraz niską wydajność procesu deweloperskiego.

---

### Kategoria 1: Kod i Architektura (AI Confusion)
1.  **Monolityczność ChartViewModel**: Przetwarzanie danych dla wszystkich zakresów (1D, 7D, 30D) oraz różnych typów wykresów (Line, Bar) w jednej dużej funkcji `processFeedsToBundles` utrudnia AI izolację błędów specyficznych dla reguł czasowych.
2.  **Implicit Scaling (baselineX/timeScale)**: Logika przesunięcia osi X i skalowania czasu jest "sprytna", ale niejawna. Agenty AI często halucynują wartości `Entry`, nie rozumiejąc, że X nie jest timestampem, lecz offsetem.
3.  **Brak Typów dla API Parameters**: Parametry takie jak `average` są przekazywane jako `Int?`. Brak definicji typu `AverageInterval` (np. enum lub sealed class) powoduje, że AI próbuje zgadywać wspierane interwały.
4.  **Stan UI (ChartState) zbyt ogólny**: Status `Loading` nie mówi AI, co jest ładowane. Powoduje to pętle "Refresh", gdy agent nie wie, czy UI czeka na dane 1D czy 30D.
5.  **Hardcoded Magic Numbers**: Wiele stałych (np. 1440, 8000, 5.0) jest rozrzuconych po kodzie zamiast być w `ChartConstants`. AI gubi się w ich znaczeniu.

### Kategoria 2: Środowisko i Śledzenie (Environment)
6.  **Ciężki Emulator (Pixel 34)**: Zużywa ogromne zasoby, co spowalnia generowanie screenshotów i powoduje, że AI widzi nieaktualny stan (np. loading spinner zamiast gotowego wykresu).
7.  **Szum w Logcat**: Standardowe `logcat *:E` zalewa AI informacjami systemowymi (thermal, battery, Bluetooth), które nie mają związku z błędem renderowania.
8.  **Długi Cykl Build-Install**: Brak użycia flag `--offline`, `--parallel` lub `--configuration-cache` sprawia, że każda poprawka trwa 2-3 minuty. AI traci "flow".
9.  **Statyczne Screenshoty**: Brak nagrań lub sekwencji screenshotów uniemożliwia AI zrozumienie, że problem jest animacyjny (np. flitering/clipping podczas scrollowania).
10. **Brak State Dump**: Agent nie ma możliwości "zrzucenia" pełnego stanu ViewModel do tekstu, musi polegać na nieprecyzyjnym logcat.

### Kategoria 3: Logika Danych (Data Logic)
11. **Konflikt results vs average**: API ThingSpeak zachowuje się nieprzewidywalnie przy obu parametrach. AI próbuje optymalizować oba naraz, co kończy się pustymi danymi.
12. **Brak Local Cache**: AI nie może sprawdzić, czy błąd jest w "pobieraniu" czy w "renderowaniu", bo każde działanie czyści stan i pobiera dane od nowa.
13. **SecurityInterceptor Overkill**: Przenoszenie `api_key` do nagłówków jest bezpieczne, ale niektóre endpointy ThingSpeak (Field Feeds) mogą go nie widzieć, co AI przeocza.
14. **Timezone Displacement**: `Instant.now()` na Windows vs Device Time. Różnice rzędu kilku sekund mogą powodować, że dane z "teraz" są odrzucane przez filtr "future data".
15. **Brak Walidacji Wejścia UseCase**: UseCase zwraca `ApiResult`, ale nie waliduje, czy otrzymana lista `FeedEntry` ma sensowne daty przed przekazaniem do ViewModel.

### Kategoria 4: Proces i Metodologia (Process)
16. **Metoda "Shotgun Debugging"**: Agenty strzelają poprawkami w UI zamiast napisać test jednostkowy dla algorytmu `average`.
17. **Saturacja Kontekstu**: Olbrzymie logi budowania i zrzuty logcat zapychają pamięć agenta, powodując zapominanie o wcześniej sprawdzonych hipotezach.
18. **Instruction Conflict**: Plik `.agent/rules` może kłócić się z globalnymi instrukcjami systemowymi, co paraliżuje decyzyjność AI.
19. **Zerowe Pokrycie Testami (Zero Coverage)**: Brak `ChartViewModelTest.kt` oznacza, że jedynym testerem jest AI patrzące na emulator. To "przepis na katastrofę".
20. **Regression Ignorance**: Optymalizacja pod 30D (BarChart) często psuje 1D (LineChart), bo agent nie ma skryptu weryfikującego oba stany naraz.

### Kategoria 5: UI & Dashboard Stabilizer
21. **Złożoność Compose Layout**: Użycie `AnimatedContent` i `PullToRefreshBox` dodaje warstwy stanów, które AI trudno śledzić bez precyzyjnych narzędzi inspekcji.
22. **Chart Clipping**: MPAndroidChart wewnątrz `LazyColumn` często gubi ramkę (clipping), co AI interpretuje jako "brak danych".
23. **Redundantne Chipy**: Chip "1D/7D/30D" w dashboardzie vs ustawienia główne. AI nie wie, który stan jest ważniejszy.
24. **Dialog-based Fullscreen**: Fullscreen w osobnym Dialogu odcina ViewModel od cyklu życia głównego ekranu w oczach AI.
25. **Brak Visual Indicators dla AI**: Brak ukrytych tagów (`contentDescription`) ułatwiających Playwright znalezienie konkretnego elementu wykresu.

### Kategoria 6: Optymalizacja i Szybkość (Speedup)
26. **Brak Mock Servera**: AI marnuje czas na czekanie na odpowiedź z serwerów ThingSpeak (USA).
27. **Złe Filtrowanie ADB**: Agent powinien używać logowania z tagiem `!!! DEBUG:`, a nie czytać wszystko.
28. **Brak Skryptów Automatyzacji**: Brak workflow typu `./check_charts.sh`, który sprawdziłby spójność danych po buildzie.
29. **Zapominanie o `task.md`**: Agenty rzadko aktualizują plan, co prowadzi do dryfu zadań ("task drift").
30. **Niska Rozdzielczość Screenshotów**: Często detale wykresu są rozmyte, co uniemożliwia AI precyzyjną diagnozę marginesów.

---

## Wnioski i Rekomendacje
1. **Wdrożyć Unit Tests**: Stworzyć `ChartViewModelTest` emulujący API ThingSpeak.
2. **Uprościć ViewModel**: Wyodrębnić `ChartDataProcessor`.
3. **Stworzyć "AI-Friendly" Debugging**: Logi z jasnymi prefixami i tekstowe zrzuty stanu.
4. **Zoptymalizować Gradle**: Dodać flagi przyspieszające build lokalnie.
