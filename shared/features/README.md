# Poravia shared feature logic

State machines, presentation values and pure transitions that Android and iOS
both use. It sits on top of `shared/core` and holds no user interface.

The rule it follows is simple: **share the decision, not the pixels and not the
words.** Each platform renders natively and takes its wording from its own string
resources; what lives here is the reasoning that would otherwise be written twice
and eventually drift, leaving two screens of the same product disagreeing about
when a coach leaves.

## What is here

| File | What it decides |
| --- | --- |
| `Loadable.kt` | Every state a screen can be in, including why a result is empty and whether a failure is worth retrying |
| `FeatureErrors.kt` | Maps a core failure onto that state by its kind, never by matching its wording |
| `Features.kt` | Journey detail, operators, stops, favourites and saved trips, the offline catalogue, release identity and sources |
| `search/SearchFeature.kt` | Place lookup with terminal and boarding-point grouping, and the search itself |
| `offline/PackDownloads.kt` | Download state and the tracker that stops a rebuilt screen starting a second download |
| `settings/SettingsState.kt` | The settings value, serialisable so it survives process death |
| `settings/SettingsFeature.kt` | What settings values are allowed to be, and repairing ones read back from storage |
| `format/Display.kt` | Clock readings in Athens time, duration parts, freshness age, byte sizes: values with no words in them |

## Two things worth knowing

**Empty is not one state.** `EmptyReason` separates "a pack for that date was read
and nothing matched" from "this device holds no timetable for that date". The
second is a statement about coverage, not about service, and rendering it as the
first would invent a certainty the data does not support.

**A geometry change must not restart a download.** `DownloadTracker` owns the
handles outside any screen, so a rotation, a fold or a split-screen resize finds
the running download instead of starting a second one.

## Tests

`commonTest` runs on the JVM and on the iOS simulator from the same sources:

```
./gradlew :shared:features:allTests
```
