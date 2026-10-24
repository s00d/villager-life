package com.vnap.client;

import com.vnap.network.DialogueAnimationPayload;
import com.vnap.network.ErrandPayload;
import com.vnap.network.NoticeBoardPayload;
import com.vnap.network.ScriptShakePayload;
import com.vnap.network.ShopListingPayload;
import com.vnap.network.VillageMoodPayload;
import com.vnap.village.market.ShopMenu;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Where each server packet goes on the client. A dedicated server loads this class too (the payload registration
 * names these methods): no screen classes here, and the player comes from the packet's context.
 */
public final class ClientPayloadHandlers {
    private ClientPayloadHandlers() {
    }

    public static void dialogueAnimation(DialogueAnimationPayload line, IPayloadContext context) {
        DialogueSoundState.start(line);
        DialogueSubtitleState.start(line);
    }

    public static void villageMood(VillageMoodPayload mood, IPayloadContext context) {
        VillageClientState.onMood(mood);
    }

    public static void scriptShake(ScriptShakePayload shake, IPayloadContext context) {
        VillageClientState.onShake(shake);
    }

    public static void errand(ErrandPayload errand, IPayloadContext context) {
        NoticeBoardClient.errand(errand);
    }

    public static void keeperMarks(com.vnap.network.KeeperMarksPayload marks, IPayloadContext context) {
        com.vnap.client.dev.DevBot.marks(marks.marks());
    }

    public static void discoveries(com.vnap.network.DiscoveriesPayload discoveries, IPayloadContext context) {
        com.vnap.client.guide.GuideBook.discovered(discoveries.keys());
    }

    public static void spireFloor(com.vnap.network.SpireFloorPayload floor, IPayloadContext context) {
        com.vnap.client.spire.SpireHud.update(floor);
    }

    public static void spireNote(com.vnap.network.SpireNotePayload note, IPayloadContext context) {
        com.vnap.client.spire.SpireClient.note(note);
    }

    public static void spireJournal(com.vnap.network.SpireJournalPayload journal, IPayloadContext context) {
        com.vnap.client.spire.SpireClient.journal(journal);
    }

    public static void noticeBoard(NoticeBoardPayload board, IPayloadContext context) {
        NoticeBoardClient.open(board);
    }

    public static void shopListing(ShopListingPayload listing, IPayloadContext context) {
        if (!(context.player().containerMenu instanceof ShopMenu shop) || shop.containerId != listing.containerId()) return;
        shop.offers = listing.offers();
        shop.sellOffer = listing.sellOffer();
        shop.sellState = listing.sellState();
        shop.keeper = listing.keeper();
        shop.ordering = listing.ordering();
    }
}
