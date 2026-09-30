"""User-visible sentences the API returns, in every product language.

These are not log lines or developer strings: a traveller reads them on the
coverage screen and in the demonstration-data notice. An English sentence
rendered on a Greek or Albanian page is a defect, so every one of them is
localised here rather than assembled in English at the point of use.

Keep the three languages in lockstep. ``localised`` refuses a partial entry, so
a missing translation is a test failure rather than a silent fallback to
English in front of a user.
"""
from __future__ import annotations

from typing import Any

from .brand import load_brand

#: The product's languages, in the order they are declared in brand.json.
LANGUAGES: tuple[str, ...] = tuple(load_brand().languages)


def localised(**translations: str) -> dict[str, str]:
    """Build a localised string, refusing anything but a complete set."""
    missing = [language for language in LANGUAGES if not translations.get(language)]
    if missing:
        raise ValueError(
            f"localised text is missing {', '.join(missing)}: {translations!r}"
        )
    extra = set(translations) - set(LANGUAGES)
    if extra:
        raise ValueError(f"localised text has unknown languages: {sorted(extra)}")
    return {language: translations[language] for language in LANGUAGES}


DEMO_COVERAGE_NOTE = localised(
    en=(
        "This release carries an invented demonstration dataset for the "
        "fictional region of Aloria. No row describes a real departure."
    ),
    el=(
        "Αυτή η έκδοση περιέχει ένα επινοημένο σύνολο δεδομένων επίδειξης για "
        "τη φανταστική περιοχή Aloria. Κανένα στοιχείο δεν περιγράφει "
        "πραγματική αναχώρηση."
    ),
    sq=(
        "Ky version përmban një grup të dhënash demonstrimi të shpikura për "
        "rajonin e trilluar Aloria. Asnjë rresht nuk përshkruan një nisje "
        "reale."
    ),
)

REAL_COVERAGE_NOTE = localised(
    en=(
        "Coverage is limited to reviewed rows from sources with documented "
        "reuse rights."
    ),
    el=(
        "Η κάλυψη περιορίζεται σε ελεγμένα στοιχεία από πηγές με τεκμηριωμένα "
        "δικαιώματα επαναχρησιμοποίησης."
    ),
    sq=(
        "Mbulimi kufizohet në rreshta të rishikuar nga burime me të drejta "
        "përdorimi të dokumentuara."
    ),
)

NOT_COVERED_SHARED = (
    localised(
        en="Real-time vehicle positions and delays are not covered.",
        el="Οι θέσεις οχημάτων σε πραγματικό χρόνο και οι καθυστερήσεις δεν καλύπτονται.",
        sq="Pozicionet e automjeteve në kohë reale dhe vonesat nuk mbulohen.",
    ),
    localised(
        en="Fares are indicative where present and are never a quotation.",
        el="Οι ναύλοι, όπου υπάρχουν, είναι ενδεικτικοί και ποτέ προσφορά.",
        sq="Çmimet, aty ku ekzistojnë, janë tregues dhe kurrë një ofertë.",
    ),
    localised(
        en="Absence of a journey is not proof that no service operates.",
        el="Η απουσία δρομολογίου δεν αποδεικνύει ότι δεν εκτελείται δρομολόγιο.",
        sq="Mungesa e një udhëtimi nuk provon se nuk kryhet asnjë shërbim.",
    ),
)

NOT_COVERED_DEMO = localised(
    en="No real Greek operator is covered. Aloria is an invented region.",
    el="Δεν καλύπτεται κανένας πραγματικός Έλληνας μεταφορέας. Η Aloria είναι επινοημένη περιοχή.",
    sq="Asnjë operator real grek nuk mbulohet. Aloria është një rajon i shpikur.",
)

NOT_COVERED_REAL = localised(
    en="Operators whose sources have no documented reuse rights are excluded.",
    el="Οι μεταφορείς των οποίων οι πηγές δεν έχουν τεκμηριωμένα δικαιώματα επαναχρησιμοποίησης εξαιρούνται.",
    sq="Operatorët, burimet e të cilëve nuk kanë të drejta përdorimi të dokumentuara, përjashtohen.",
)


def coverage_note(data_mode: str) -> dict[str, str]:
    return DEMO_COVERAGE_NOTE if data_mode == "demo" else REAL_COVERAGE_NOTE


def not_covered(data_mode: str) -> list[dict[str, str]]:
    leading = NOT_COVERED_DEMO if data_mode == "demo" else NOT_COVERED_REAL
    return [leading, *NOT_COVERED_SHARED]


def all_localised_texts() -> list[dict[str, Any]]:
    """Every localised string this module exposes, for completeness tests."""
    return [
        DEMO_COVERAGE_NOTE,
        REAL_COVERAGE_NOTE,
        NOT_COVERED_DEMO,
        NOT_COVERED_REAL,
        *NOT_COVERED_SHARED,
    ]
