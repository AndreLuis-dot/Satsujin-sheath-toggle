package com.sheathtoggle.compat;

import com.sheathtoggle.SheathToggle;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ponte por reflexao para Epic Fight e Weapons of Miracles.
 *
 * Usa reflexao de proposito: assim o projeto compila so com o Forge, sem precisar dos .jar
 * dos outros mods no repositorio. Se algum nome mudar, o mod nao quebra o jogo: ele registra
 * um aviso no latest.log (procure por "sheathtoggle") e fica inerte.
 *
 * Armas suportadas (a tecla decide pela passiva da arma que esta na mao):
 *  - Satsujin: guardar / sacar a espada (dado SHEATH da SatsujinPassive).
 *  - Moonless: trocar o modo da lua (dado VERSO da LunarEchoPassiveSkill).
 */
public final class EpicBridge {

    private static final String SATSUJIN_PASSIVE = "reascer.wom.skill.weaponpassive.SatsujinPassive";
    private static final String LUNAR_ECHO_PASSIVE = "reascer.wom.skill.weaponpassive.LunarEchoPassiveSkill";

    // 0 = nao tentou, 1 = ok, -1 = indisponivel
    private static int state = 0;
    private static boolean satsujinOk = false;
    private static boolean moonOk = false;

    private static Class<?> capsClass;
    private static Class<?> serverPlayerPatchClass;
    private static Object weaponPassiveSlot;
    private static Object[] allSlots;

    private static Class<?> animsSatsujinClass;
    private static Object sheathKeyHolder;
    private static Object versoKeyHolder;

    private static boolean warnedRuntime = false;
    private static final Map<String, Method> METHOD_CACHE = new ConcurrentHashMap<>();

    private EpicBridge() {
    }

    private static synchronized boolean init() {
        if (state != 0) return state == 1;
        try {
            capsClass = Class.forName("yesman.epicfight.world.capabilities.EpicFightCapabilities");
            serverPlayerPatchClass = Class.forName("yesman.epicfight.world.capabilities.entitypatch.player.ServerPlayerPatch");

            Class<?> slots = Class.forName("yesman.epicfight.skill.SkillSlots");
            weaponPassiveSlot = slots.getField("WEAPON_PASSIVE").get(null);
            Object[] constants = slots.getEnumConstants();
            allSlots = constants != null ? constants : new Object[]{weaponPassiveSlot};
            state = 1;
        } catch (Throwable t) {
            state = -1;
            SheathToggle.LOGGER.warn("[sheathtoggle] Epic Fight indisponivel ou com nomes diferentes; o mod ficara inerte. Motivo: {}", t.toString());
            return false;
        }

        try {
            Class<?> keys = Class.forName("reascer.wom.skill.WOMSkillDataKeys");
            sheathKeyHolder = keys.getField("SHEATH").get(null);
            animsSatsujinClass = Class.forName("reascer.wom.gameasset.animations.weapons.AnimsSatsujin");
            satsujinOk = true;
            SheathToggle.LOGGER.info("[sheathtoggle] Satsujin encontrada.");
        } catch (Throwable t) {
            SheathToggle.LOGGER.warn("[sheathtoggle] Suporte a Satsujin indisponivel. Motivo: {}", t.toString());
        }

        try {
            Class<?> keys = Class.forName("reascer.wom.skill.WOMSkillDataKeys");
            versoKeyHolder = keys.getField("VERSO").get(null);
            moonOk = true;
            SheathToggle.LOGGER.info("[sheathtoggle] Moonless encontrada.");
        } catch (Throwable t) {
            SheathToggle.LOGGER.warn("[sheathtoggle] Suporte a Moonless indisponivel. Motivo: {}", t.toString());
        }
        return true;
    }

    // ---------------------------------------------------------------- API

    /** Zera o timer de auto-guardar da Satsujin (se a passiva dela estiver ativa). */
    public static void suppressAutoSheath(ServerPlayer player) {
        if (!init() || !satsujinOk) return;
        try {
            Object patch = getPatch(player);
            if (patch == null) return;
            Object container = findContainer(patch, SATSUJIN_PASSIVE);
            if (container == null) return;
            Object skill = call(container, "getSkill");
            call(skill, "setConsumption", container, 0.0F);
        } catch (Throwable t) {
            warnRuntime("suppressAutoSheath", t);
        }
    }

    /**
     * Acao da tecla V: depende da arma na mao.
     * Satsujin -> guarda/saca a espada. Moonless -> troca o modo da lua.
     * Retorna true se alguma coisa foi alternada.
     */
    public static boolean toggleStance(ServerPlayer player) {
        if (!init()) return false;
        try {
            Object patch = getPatch(player);
            if (patch == null) return false;

            if (satsujinOk) {
                Object container = findContainer(patch, SATSUJIN_PASSIVE);
                if (container != null) return toggleSheath(player, patch, container);
            }
            if (moonOk) {
                Object container = findContainer(patch, LUNAR_ECHO_PASSIVE);
                if (container != null) return toggleMoon(player, patch, container);
            }
            return false; // nenhuma arma suportada na mao
        } catch (Throwable t) {
            warnRuntime("toggleStance", t);
            return false;
        }
    }

    // ---------------------------------------------------------------- Satsujin

    private static boolean toggleSheath(ServerPlayer player, Object patch, Object container) throws Exception {
        if (isInAction(patch)) return false; // no meio de uma animacao

        Object dataManager = call(container, "getDataManager");
        Object key = call(sheathKeyHolder, "get");
        boolean sheathed = (Boolean) call(dataManager, "getDataValue", key);
        boolean next = !sheathed;

        // Mesma sequencia que a SatsujinPassive original usa.
        call(dataManager, "setDataSync", key, next);
        call(patch, "modifyLivingMotionByCurrentItem", false);
        if (next) {
            Object anim = animsSatsujinClass.getField("SATSUJIN_SHEATHE").get(null);
            call(patch, "playAnimationInClientSide", anim, 0.0F);
        }

        player.displayClientMessage(Component.translatable(
                next ? "message.sheathtoggle.sheathed" : "message.sheathtoggle.drawn"), true);
        return true;
    }

    // ---------------------------------------------------------------- Moonless

    /**
     * Na Moonless, o "modo" e o dado VERSO da LunarEchoPassiveSkill: false = estilo TWO_HAND,
     * true = estilo OCHS (cada um com seu combo). O jogo original inverte esse dado no inicio dos
     * ataques de corrida MOONLESS_REVERSED_BYPASS (liga) e MOONLESS_BYPASS (desliga), e logo em
     * seguida chama modifyLivingMotionByCurrentItem(false). Aqui fazemos o mesmo pela tecla.
     */
    private static boolean toggleMoon(ServerPlayer player, Object patch, Object container) throws Exception {
        if (isInAction(patch)) return false;

        Object dataManager = call(container, "getDataManager");
        Object key = call(versoKeyHolder, "get");
        boolean verso = false;
        Object has = call(dataManager, "hasData", key);
        if (has instanceof Boolean hasData && hasData) {
            Object value = call(dataManager, "getDataValue", key);
            verso = value instanceof Boolean b && b;
        }

        call(dataManager, "setDataSync", key, !verso);
        call(patch, "modifyLivingMotionByCurrentItem", false);

        player.displayClientMessage(Component.literal("The moon has changed"), true);
        return true;
    }

    // ---------------------------------------------------------------- helpers

    private static boolean isInAction(Object patch) throws Exception {
        Object entityState = call(patch, "getEntityState");
        return (Boolean) call(entityState, "inaction");
    }

    private static Object getPatch(ServerPlayer player) throws Exception {
        return callStatic(capsClass, "getEntityPatch", player, serverPlayerPatchClass);
    }

    /** Acha o SkillContainer cuja skill tem o nome de classe dado (slot WEAPON_PASSIVE primeiro). */
    private static Object findContainer(Object patch, String skillClassName) {
        Object c = containerIfSkill(patch, weaponPassiveSlot, skillClassName);
        if (c != null) return c;
        for (Object slot : allSlots) {
            if (slot == weaponPassiveSlot) continue;
            c = containerIfSkill(patch, slot, skillClassName);
            if (c != null) return c;
        }
        return null;
    }

    private static Object containerIfSkill(Object patch, Object slot, String skillClassName) {
        try {
            Object container = call(patch, "getSkill", slot);
            if (container == null) return null;
            Object skill = call(container, "getSkill");
            if (skill != null && skill.getClass().getName().equals(skillClassName)) return container;
        } catch (Throwable ignored) {
            // slot sem container etc.
        }
        return null;
    }

    private static void warnRuntime(String where, Throwable t) {
        if (warnedRuntime) return;
        warnedRuntime = true;
        Throwable root = t;
        while (root.getCause() != null) root = root.getCause();
        SheathToggle.LOGGER.warn("[sheathtoggle] Falha em {} (aviso mostrado so uma vez): {}", where, root.toString(), root);
    }

    private static Object call(Object target, String name, Object... args) throws Exception {
        Method m = findMethod(target.getClass(), name, args);
        return m.invoke(target, args);
    }

    private static Object callStatic(Class<?> type, String name, Object... args) throws Exception {
        Method m = findMethod(type, name, args);
        return m.invoke(null, args);
    }

    private static Method findMethod(Class<?> type, String name, Object[] args) throws NoSuchMethodException {
        StringBuilder keyBuilder = new StringBuilder(type.getName()).append('#').append(name);
        for (Object a : args) keyBuilder.append('|').append(a == null ? "null" : a.getClass().getName());
        String key = keyBuilder.toString();

        Method cached = METHOD_CACHE.get(key);
        if (cached != null) return cached;

        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (matches(m, name, args)) return remember(key, m);
            }
        }
        for (Method m : type.getMethods()) { // metodos default de interfaces
            if (matches(m, name, args)) return remember(key, m);
        }
        throw new NoSuchMethodException(type.getName() + "#" + name + " com " + args.length + " argumento(s)");
    }

    private static Method remember(String key, Method m) {
        m.setAccessible(true);
        METHOD_CACHE.put(key, m);
        return m;
    }

    private static boolean matches(Method m, String name, Object[] args) {
        if (!m.getName().equals(name) || m.isBridge() || m.getParameterCount() != args.length) return false;
        Class<?>[] params = m.getParameterTypes();
        for (int i = 0; i < params.length; i++) {
            if (args[i] == null) {
                if (params[i].isPrimitive()) return false;
                continue;
            }
            Class<?> p = params[i].isPrimitive() ? wrap(params[i]) : params[i];
            if (!p.isInstance(args[i])) return false;
        }
        return true;
    }

    private static Class<?> wrap(Class<?> p) {
        if (p == boolean.class) return Boolean.class;
        if (p == float.class) return Float.class;
        if (p == int.class) return Integer.class;
        if (p == double.class) return Double.class;
        if (p == long.class) return Long.class;
        if (p == byte.class) return Byte.class;
        if (p == short.class) return Short.class;
        if (p == char.class) return Character.class;
        return p;
    }
}
