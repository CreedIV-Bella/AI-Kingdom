package com.aibots.common.entity;

import com.aibots.common.ai.WorldFactGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;
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
import java.util.ArrayDeque;
import java.util.Deque;

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
    private static final String DIALOGUE_KEY = "AIKingdomsDialogue";
    private static final int MAX_DIALOGUE_LINES = 12;
    private static final int MAX_LINE_LENGTH = 360;
    private final Deque<String> dialogue = new ArrayDeque<>();

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

    private static final String SKIN_KEY = "AIKingdomsSkinVariant";
    private static final String MOVE_MODE_KEY = "AIKingdomsMoveMode";
    private static final String FOLLOW_UUID_KEY = "AIKingdomsFollowUUID";
    private static final String TARGET_X = "AIKingdomsTargetX";
    private static final String TARGET_Y = "AIKingdomsTargetY";
    private static final String TARGET_Z = "AIKingdomsTargetZ";
    private static final EntityDataAccessor<Integer> DATA_SKIN_VARIANT =
            SynchedEntityData.defineId(AiBotEntity.class, EntityDataSerializers.INT);

    public enum MoveMode { STAY, FOLLOW, GO_TO, WANDER }

    private MoveMode moveMode = MoveMode.STAY;
    private UUID followPlayerUUID;
    private BlockPos destination;
    private String travelReport = "Inväntar order.";
    private int unreachableTicks;

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

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_SKIN_VARIANT, 0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new KingdomTravelGoal(this));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8D) {
            @Override
            public boolean canUse() {
                return moveMode == MoveMode.WANDER && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return moveMode == MoveMode.WANDER && super.canContinueToUse();
            }
        });
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    public int getSkinVariant() {
        return entityData.get(DATA_SKIN_VARIANT);
    }

    public MoveMode getMoveMode() {
        return moveMode;
    }

    public BlockPos getDestination() {
        return destination;
    }

    public UUID getFollowPlayerUUID() {
        return followPlayerUUID;
    }

    public void followPlayer(ServerPlayer player) {
        moveMode = MoveMode.FOLLOW;
        followPlayerUUID = player.getUUID();
        destination = null;
        unreachableTicks = 0;
        travelReport = "Följer kungen.";
        getNavigation().stop();
    }

    public void goTo(BlockPos pos) {
        moveMode = MoveMode.GO_TO;
        followPlayerUUID = null;
        destination = pos.immutable();
        unreachableTicks = 0;
        travelReport = "På väg till " + destination.toShortString() + ".";
        getNavigation().stop();
    }

    public void stay() {
        moveMode = MoveMode.STAY;
        followPlayerUUID = null;
        destination = null;
        unreachableTicks = 0;
        travelReport = "Stannar här.";
        getNavigation().stop();
    }

    public void wander() {
        moveMode = MoveMode.WANDER;
        followPlayerUUID = null;
        destination = null;
        unreachableTicks = 0;
        travelReport = "Vandrar omkring.";
        getNavigation().stop();
    }

    public void arrived() {
        moveMode = MoveMode.STAY;
        followPlayerUUID = null;
        destination = null;
        unreachableTicks = 0;
        travelReport = "Framme vid destinationen.";
        getNavigation().stop();
    }

    public void pathFailed() {
        moveMode = MoveMode.STAY;
        followPlayerUUID = null;
        destination = null;
        unreachableTicks = 0;
        travelReport = "Kan inte hitta en säker väg. Ge mig en närmare destination.";
        getNavigation().stop();
    }

    public boolean recordPathFailure() {
        return ++unreachableTicks >= 5;
    }

    public void clearPathFailures() {
        unreachableTicks = 0;
    }

    public String getTravelReport() {
        return travelReport;
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
                + "Svara naturligt och varmt, oftast med 2-4 meningar, eller längre när spelaren vill ha detaljer. "
                + "Anknyt gärna till det senaste samtalet och ställ ibland en relevant följdfråga. "
                + "Upprepa inte ers majestät eller andra högtidliga fraser i varje svar. "
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

    /**
     * Some answers must come from game code instead of a generative model.
     * Returns null only when the LLM may be consulted.
     */
    public String verifiedReply(String message) {
        String identity = fixedIdentityReply(message);
        return identity != null ? identity : WorldFactGuard.replyIfUnknown(message);
    }

    /** Only the last six conversation exchanges are retained by this NPC.
     * Dialogue is persisted with the entity NBT and is NOT a database of facts. */
    public void rememberExchange(String user, String reply) {
        if (user == null || reply == null || user.isBlank() || reply.isBlank()
                || reply.startsWith("Request failed:")
                || reply.startsWith("API error ")) {
            return;
        }
        appendDialogue("Spelaren", user);
        appendDialogue(botName(), reply);
    }

    private void appendDialogue(String who, String content) {
        String clean = content.replace('\n', ' ').replace('\r', ' ').strip();
        if (clean.length() > MAX_LINE_LENGTH) {
            clean = clean.substring(0, MAX_LINE_LENGTH);
        }
        dialogue.addLast(who + ": " + clean);
        while (dialogue.size() > MAX_DIALOGUE_LINES) {
            dialogue.removeFirst();
        }
    }

    public String messageWithHistory(String current) {
        if (dialogue.isEmpty()) {
            return current;
        }
        StringBuilder prompt = new StringBuilder();
        prompt.append("TIDIGARE SAMTAL (endast konversationsminne, INTE verifierade speldata).\n");
        for (String line : dialogue) {
            prompt.append(line).append('\n');
        }
        prompt.append("SLUT PÅ TIDIGARE SAMTAL.\n")
                .append("Svara på senaste frågan och håll samma personlighet.\n")
                .append("SENASTE FRÅGA: ").append(current);
        return prompt.toString();
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
        // Royal citizens must never despawn just because a player is far away.
        setPersistenceRequired();
        if (getCustomName() == null) {
            String firstName = FIRST_NAMES[random.nextInt(FIRST_NAMES.length)];
            String lastName = LAST_NAMES[random.nextInt(LAST_NAMES.length)];
            setCustomName(Component.literal(firstName + " " + lastName));
        }
        setCustomNameVisible(true);
        if (personality.isBlank()) {
            personality = TRAITS[random.nextInt(TRAITS.length)];
        }
        entityData.set(DATA_SKIN_VARIANT, Math.floorMod(getUUID().hashCode(), 4));
        identityInitialized = true;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString(ROLE_KEY, role);
        tag.putString(PERSONALITY_KEY, personality);
        tag.putBoolean(INITIALIZED_KEY, identityInitialized);
        ListTag history = new ListTag();
        for (String line : dialogue) {
            history.add(StringTag.valueOf(line));
        }
        tag.put(DIALOGUE_KEY, history);
        tag.putInt(SKIN_KEY, getSkinVariant());
        tag.putString(MOVE_MODE_KEY, moveMode.name());
        if (followPlayerUUID != null) {
            tag.putUUID(FOLLOW_UUID_KEY, followPlayerUUID);
        }
        if (destination != null) {
            tag.putInt(TARGET_X, destination.getX());
            tag.putInt(TARGET_Y, destination.getY());
            tag.putInt(TARGET_Z, destination.getZ());
        }
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
        dialogue.clear();
        ListTag history = tag.getList(DIALOGUE_KEY, 8);
        for (int i = Math.max(0, history.size() - MAX_DIALOGUE_LINES);
                i < history.size(); i++) {
            String line = history.getString(i);
            if (!line.isBlank()) {
                dialogue.addLast(line.substring(0, Math.min(line.length(),
                        MAX_LINE_LENGTH + 100)));
            }
        }
        entityData.set(DATA_SKIN_VARIANT, tag.contains(SKIN_KEY, 3)
                ? Math.floorMod(tag.getInt(SKIN_KEY), 4)
                : Math.floorMod(getUUID().hashCode(), 4));

        // Older saves have no movement instructions and default to STAY.
        try {
            moveMode = MoveMode.valueOf(tag.getString(MOVE_MODE_KEY));
        } catch (IllegalArgumentException ignored) {
            moveMode = MoveMode.STAY;
        }
        followPlayerUUID = tag.hasUUID(FOLLOW_UUID_KEY) ? tag.getUUID(FOLLOW_UUID_KEY) : null;
        destination = tag.contains(TARGET_X, 3) && tag.contains(TARGET_Y, 3)
                && tag.contains(TARGET_Z, 3)
                ? new BlockPos(tag.getInt(TARGET_X), tag.getInt(TARGET_Y), tag.getInt(TARGET_Z))
                : null;
        if ((moveMode == MoveMode.FOLLOW && followPlayerUUID == null)
                || (moveMode == MoveMode.GO_TO && destination == null)) {
            moveMode = MoveMode.STAY;
        }
        travelReport = "Order: " + moveMode.name();
        // Protect NPCs created by older builds too, not just newly summoned NPCs.
        setPersistenceRequired();
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
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
