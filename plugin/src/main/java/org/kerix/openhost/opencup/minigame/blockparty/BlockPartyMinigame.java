package org.kerix.openhost.opencup.minigame.blockparty;

import org.kerix.openhost.opencup.api.minigame.EndReason;
import org.kerix.openhost.opencup.api.minigame.Minigame;
import org.kerix.openhost.opencup.api.minigame.MinigameDescriptor;
import org.kerix.openhost.opencup.api.minigame.MinigameResult;

@MinigameDescriptor(id = "blockparty", displayName = "Block Party")
public class BlockPartyMinigame extends Minigame {

    @Override
    public void onStart() {

    }

    @Override
    public MinigameResult onEnd(EndReason reason) {
        return null;
    }
}
