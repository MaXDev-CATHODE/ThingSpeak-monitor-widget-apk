# Plan Stabilizacji Renderowania Dashboardu

Zadanie polega na usunięciu powtarzalnych błędów w renderowaniu danych 30D oraz zabezpieczeniu procesu przed regresją.

## Proponowane Zmiany

### 1. Refaktoryzacja Logiki Danych
#### [ChartViewModel.kt](file:///f:/PROJEKTY/ThingSpeak%20Monitor%20Widget%20APK/app/src/main/java/com/thingspeak/monitor/feature/chart/presentation/ChartViewModel.kt)
- **Ekstrakcja Logic**: Wydzielenie `ChartDataProcessor` jako osobnej klasy czysto logicznej (bez zależności od ViewModel).
- **Weryfikacja parametrów**: Naprawa algorytmu wybierającego `average` i `results`. Priorytetem będzie teraz spójność zakresu nad gęstością.
- **State Granularity**: Rozszerzenie `ChartState` o fazy ładowania specyficzne dla pól.

### 2. Wdrożenie Testów (Klucz do sukcesu)
#### [NEW] [ChartDataProcessorTest.kt](file:///f:/PROJEKTY/ThingSpeak%20Monitor%20Widget%20APK/app/src/test/java/com/thingspeak/monitor/feature/chart/ChartDataProcessorTest.kt)
- Testy jednostkowe dla algorytmu skalowania X (offset vs time).
- Testy dla konwersji `FeedEntry` -> `Entry/BarEntry` dla zakresów 1D i 30D.
- Weryfikacja logiki filtrów `AVG`, `MEDIAN`, `SUM`.

### 3. Stabilizacja Środowiska AI
- **Implementacja Logowania**: Dodanie tagu `!!! AGENT_DEBUG` do kluczowych metod w ViewModel.
- **Workflow Build Optimization**: Aktualizacja `build.md` w `.agent/workflows` o optymalizacje Gradle.

## Plan Weryfikacji

### Testy Automatyczne
1. `./gradlew test` - musi przejść dla nowego `ChartDataProcessorTest`.
2. `adb logcat -s "!!! AGENT_DEBUG"` - weryfikacja czy dane wejściowe z API są poprawne przed renderowaniem.

### Weryfikacja Manualna
1. **Scenariusz 30D**: Otwarcie dashboardu, wybór 30D. Wykres musi pokazać pełne słupeczki (BarChart) bez ucinania na końcu.
2. **Scenariusz Przełączania**: Szybkie przełączanie 1D -> 30D -> 1D. UI nie może "zamrozić się" na loading spinnerze.
3. **Emulator Stability**: Brak migotania wykresu przy zmianie `Smoothing`.
