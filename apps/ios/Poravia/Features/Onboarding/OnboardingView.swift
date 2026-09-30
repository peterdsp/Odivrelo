import SwiftUI

/// First launch.
///
/// No account, no permission request, no network call and no screen a person
/// can be stuck behind. It states what the product does and does not do,
/// offers the language choice, and goes straight to search.
struct OnboardingView: View {
    @Environment(AppSettings.self) private var settings
    @Environment(\.windowGeometry) private var geometry

    var body: some View {
        @Bindable var settings = settings

        ScrollView {
            VStack(alignment: .leading, spacing: Theme.Space.large) {
                header

                SectionCard(L10n.onboardingLanguage, systemImage: "character.bubble") {
                    // A list of choices rather than a wheel: on a first launch
                    // every option should be readable at a glance, and a wheel
                    // hides all but one at large text sizes.
                    VStack(spacing: 0) {
                        languageRow(nil, label: L10n.settingsLanguageSystem)
                        ForEach(L10n.supportedLanguages, id: \.self) { tag in
                            Divider()
                            languageRow(tag, label: Self.languageName(tag))
                        }
                    }
                    .accessibilityElement(children: .contain)
                    .accessibilityLabel(L10n.onboardingLanguage)
                }

                SectionCard(L10n.onboardingHonestyTitle, systemImage: "checkmark.seal") {
                    VStack(alignment: .leading, spacing: Theme.Space.small) {
                        promise(L10n.onboardingNoTickets, systemImage: "ticket", tone: .warning)
                        promise(L10n.onboardingNoAccount, systemImage: "person.slash", tone: .neutral)
                        promise(L10n.onboardingNoTracking, systemImage: "eye.slash", tone: .neutral)
                        promise(L10n.onboardingOfflineFirst, systemImage: "arrow.down.circle", tone: .info)
                    }
                }

                Button(L10n.onboardingStart) {
                    settings.hasCompletedOnboarding = true
                }
                .buttonStyle(PoraviaPrimaryButtonStyle())
                .accessibilityIdentifier("onboarding.start")
            }
            .readableColumn(geometry)
            .padding(.vertical, Theme.Space.large)
        }
        .background(Theme.Palette.background)
        .scrollBounceBehavior(.basedOnSize)
    }

    private func languageRow(_ tag: String?, label: String) -> some View {
        let isSelected = settings.languageTag == tag
        return Button {
            settings.languageTag = tag
        } label: {
            HStack(spacing: Theme.Space.small) {
                Text(label)
                    .font(.body)
                    .foregroundStyle(Theme.Palette.textPrimary)
                    .multilineTextAlignment(.leading)
                    .fixedSize(horizontal: false, vertical: true)
                Spacer(minLength: 0)
                Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                    .foregroundStyle(
                        isSelected ? Theme.Palette.primary : Theme.Palette.border
                    )
                    .accessibilityHidden(true)
            }
            .frame(minHeight: Theme.Size.touchMinimum)
            .contentShape(.rect)
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isSelected ? [.isButton, .isSelected] : .isButton)
        .accessibilityIdentifier("onboarding.language.\(tag ?? "system")")
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: Theme.Space.small) {
            WordmarkView()
            Text(L10n.onboardingTitle)
                .font(.largeTitle.bold())
                .foregroundStyle(Theme.Palette.textPrimary)
                .accessibilityAddTraits(.isHeader)
            Text(Brand.tagline(forLanguageTag: settings.effectiveLanguageTag))
                .font(.title3)
                .foregroundStyle(Theme.Palette.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    private func promise(_ text: String, systemImage: String, tone: Tone) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: Theme.Space.small) {
            Image(systemName: systemImage)
                .foregroundStyle(tone.text)
                .frame(width: Theme.Size.icon)
                .accessibilityHidden(true)
            Text(text)
                .font(.subheadline)
                .foregroundStyle(Theme.Palette.textPrimary)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityElement(children: .combine)
    }

    /// The language's own name, which is what a reader looking for their
    /// language recognises. Flags are never used for a language.
    static func languageName(_ tag: String) -> String {
        Locale(identifier: tag).localizedString(forLanguageCode: tag)?.localizedCapitalized ?? tag
    }
}

#Preview("Onboarding") {
    OnboardingView()
        .environment(AppSettings())
        .environment(\.windowGeometry, WindowGeometry(
            size: CGSize(width: 393, height: 852),
            safeArea: EdgeInsets(top: 59, leading: 0, bottom: 34, trailing: 0)
        ))
}
