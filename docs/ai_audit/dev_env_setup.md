# Konfiguracja Środowiska AI-Resilient: ThingSpeak Monitor

Aby zapobiec "gubieniu się" agentów w Pixelu i przyspieszyć pracę, wdrażamy następujące standardy środowiskowe.

## 1. Protokół Debugowania (AI-First Logging)
Zamiast czytać surowy Logcat, agenty będą korzystać z dedykowanego tagu:
- **Tag**: `!!! AGENT_DEBUG`
- **Komenda odczytu**: `adb logcat -s "!!! AGENT_DEBUG"`
- **Cel**: Wyeliminowanie 99% szumu systemowego.

## 2. Mechanizm State Dump
W `ChartViewModel` wprowadzamy funkcję `dumpState()`, która:
- Zapisuje aktualny stan UI (zakres, wybrane pola, filtry, ilość punktów) do pliku `/sdcard/Download/vm_state.json`.
- Agent może wywołać `adb pull` po restarcie, aby natychmiast odzyskać kontekst bez zgadywania ze screenshotów.

## 3. Optymalizacja Builda (local.gradle)
Dodajemy/edytujemy `gradle.properties` oraz skrypty, aby wymusić:
- `org.gradle.parallel=true`
- `org.gradle.caching=true`
- `org.gradle.jvmargs=-Xmx4g`
- Unikanie `clean` przy każdej operacji.

## 4. Tryb Mock / Offline (Stability)
- Wprowadzenie flagi `useMockData` w `local.properties`.
- Gdy `true`, `GetHistoricalDataUseCase` zwraca dane z `assets/mock_feeds.json` zamiast z sieci.
- Pozwala to na testowanie renderowania bez opóźnień API i problemów z kluczem.

## 5. Checklist Przed-Zadaniowy dla Agenta
Agent przed rozpoczęciem pracy MUSI sprawdzić:
1. `adb shell settings put global stay_on_while_plugged_in 3` (zapobieganie wygaszaniu ekranu).
2. `adb shell date` (synchronizacja czasu z hostem).
3. `adb shell wm size` (potwierdzenie rozdzielczości emulacji).

---
**Cel**: Skrócenie czasu pętli zwrotnej z 5 minut do <60 sekund.
