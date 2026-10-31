import SwiftUI

/**
 The way a row arrives on this product's screens.

 A departures board settles: each row drops the last fraction of its travel and
 comes to rest, one after another down the board. That is the one motion this
 product has a reason to own, because the board is the object the whole design
 is built as. Everywhere else, things simply appear.

 Movement only, never a fade through unreadable text. The web layer began with
 opacity fades and a paragraph sampled 300 milliseconds in failed the contrast
 check: for a few hundred milliseconds the words really were too faint to read,
 which is a reading fault rather than an awkward measurement. Text here travels
 into place at full strength, so there is no moment at which it cannot be read.

 Reduce Motion removes the travel and keeps the content, which is the whole
 contract: nothing is ever conveyed by the movement alone.
 */
struct BoardEntrance: ViewModifier {
    /// Where this row sits in its board, counting from zero.
    let index: Int

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var settled = false

    private var delay: Double {
        // Each row follows the one above by a beat, and the stagger stops after
        // the sixth so a long board does not keep a reader waiting on motion.
        min(Double(index), 5) * 0.05
    }

    func body(content: Content) -> some View {
        content
            .scaleEffect(y: settled || reduceMotion ? 1 : 0.84, anchor: .top)
            .offset(y: settled || reduceMotion ? 0 : -6)
            .animation(
                reduceMotion
                    ? nil
                    : .timingCurve(0.2, 0.8, 0.2, 1, duration: Theme.Motion.standard)
                        .delay(delay),
                value: settled
            )
            .onAppear { settled = true }
    }
}

extension View {
    /// Arrives the way a row on a departures board does.
    func boardEntrance(index: Int) -> some View {
        modifier(BoardEntrance(index: index))
    }
}
