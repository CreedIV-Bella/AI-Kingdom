package com.aibots.common.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.Locale;

/**
 * An AI Kingdoms citizen with a persistent name and role-playing identity.
 *
 * The LLM never chooses the NPC's factual identity during a conversation:
 * the saved entity data is the source of truth.
 */
public class AiBotEntity extends PathfinderMob {

    private static final String ROLE_KEY = "AIKingdomsRole";
    private static final String PERSONALITY_KEY = "AIKingdomsPersonality";
    private static final String INITIALIZED_KEY = "AIKingdomsIdentityInitialized";

    private static final String[] FIRST_NAMES = {
            "Silas", "Aldric", "Edmund", "Cedric", "Valerius", "Elara",
            "Isolde", "Astrid", "Freya", "Rowan", "Lucian", "Evelyn",
            "Gideon", "Marianne", "Alaric", "Seraphina"
    };
    private static final String[] LAST_NAMES = {
            "Blackwood", "Ashford", "Ravencrest", "Westbrook", "Ironwood",
            "Silvermere", "Thornfield", "Stormwatch", "Oakheart",
            "Hawthorne", "Brightwater", "Dunmore"
    };
    private static final String[] TRAITS = {
            "lugn, analytisk och noggrann",
            "nyfiken, vänlig och uppfinningsrik",
            "försiktig, diplomatisk och eftertänksam",
            "rättfram, lojal och strategisk",
            "humoristisk, modig och praktiskt lagd",
            "ambitiös, organiserad och uppmärksam"
    };

    private String role = "kunglig rådgivare";
    private String personality = "";
    private boolean identityInitialized;

    public AiBotEntity(EntityType<? extends AiBotEntity> type, Level level) {
        super(type, level);
    }

    /** Preserve callers' ability to customize a particular NPC's personality. */
    public void setPersonality(String personality) {
        if (personality != null && !personality.isBlank()) {
            this.personality = personality;
        }
    }

    public void setRole(String role) {
        if (role != null && !role.isBlank()) {
            this.role = role;
        }
    }

    public String getRole() {
        return role;
    }

    /**
     * The per-NPC system prompt overrides the generic configuration prompt.
     * Derive it from persisted entity state, rather than trusting AI-made claims.
     */
    public String getPersonality() {
        ensureIdentity();
        return "ROLLSPEL I MINECRAFT: Du är en NPC i AI Kingdoms. "
                + "Din fasta identitet är: NAMN = '" + botName() + "' och YRKE = '" + role + "'. "
                + "Ditt personlighetsdrag är: " + personality + ". "
                + "Du får ALDRIG byta namn, hitta på ett nytt namn åt dig själv eller hävda att du är någon annan. "
                + "Spelaren är kungen. Svara ENBART på svenska, oavsett vilket språk spelaren använder. "
                + "Svara kort, helst 1-3 meningar, utan långa inledningar. "
                + "Om spelaren frågar om din identitet, använd exakt det givna namnet och yrket. "
                + "Du får hitta på fiktiva berättelser och förslag, men märk dem tydligt som fantasi eller förslag. "
                + "Du har INGEN automatisk åtkomst till spelvärldens resurser, arméer, städer eller byggnader. "
                + "Gissa ALDRIG speldata och påstå ALDRIG att du har utfört uppdrag. "
                + "Om verifierad information saknas, säg tydligt att du inte vet.";
    }

    /**
     * Factual identity belongs to the entity, not the language model.
     * Answer simple identity questions without involving an LLM that may invent a name.
     */
    public String fixedIdentityReply(String userMessage) {
        if (userMessage == null) {
            return null;
        }
        String question = userMessage.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[?!.]+$", "").trim();
        if (question.equals("vad heter du")
                || question.equals("vad är ditt namn")
                || question.equals("vem är du")
                || question.equals("what is your name")
                || question.equals("who are you")) {
            return "Jag heter " + botName() + " och är " + role + " i AI Kingdoms.";
        }
        return null;
    }

    public String botName() {
        Component name = getCustomName();
        return name != null ? name.getString() : "AI Bot";
    }

    /**
     * Initialize on the server after spawning, without overwriting explicit
     * names supplied by /summon or a name tag.
     */
    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && !identityInitialized) {
            ensureIdentity();
        }
    }

    private void ensureIdentity() {
        if (identityInitialized || level().isClientSide) {
            return;
        }
        if (getCustomName() == null) {
            String firstName = FIRST_NAMES[random.nextInt(FIRST_NAMES.length)];
            String lastName = LAST_NAMES[random.nextInt(LAST_NAMES.length)];
            setCustomName(Component.literal(firstName + " " + lastName));
        }
        setCustomNameVisible(true);
        if (personality.isBlank()) {
            personality = TRAITS[random.nextInt(TRAITS.length)];
        }
        identityInitialized = true;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString(ROLE_KEY, role);
        tag.putString(PERSONALITY_KEY, personality);
        tag.putBoolean(INITIALIZED_KEY, identityInitialized);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains(ROLE_KEY, 8)) {
            role = tag.getString(ROLE_KEY);
        }
        if (tag.contains(PERSONALITY_KEY, 8)) {
            personality = tag.getString(PERSONALITY_KEY);
        }
        identityInitialized = tag.getBoolean(INITIALIZED_KEY);
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!player.level().isClientSide) {
            ensureIdentity();
            PlayerConversation.setTarget(player, this);
            player.displayClientMessage(
                    Component.literal("Talking to " + botName() + ". Use /ask <message>."),
                    false);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.CONSUME;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.35D)
                .add(Attributes.FOLLOW_RANGE, 16.0D);
    }
}
