package com.sheathtoggle.compat;

import com.sheathtoggle.SheathToggle;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ponte por reflexao para Epic Fight e Weapons of Miracles.
 *
 * Usa reflexao de proposito: assim o projeto compila so com o Forge, sem precisar dos .jar
 * dos outros mods no repositorio. Se algum nome mudar, o mod nao quebra o jogo: ele registra
 * um aviso no latest.log (procure por "sheathtoggle") e fica inerte.
 */
public final class EpicBridge {

    private static final String SATSUJIN_PASSIVE = "reascer.wom.skill.weaponpassive.SatsujinPassive";

    // 0 = nao tentou, 1 = ok, -1 = indisponivel
    private static int state = 0;

    private static Class<?> capsClass;
    private static Class<?> serverPlayerPatchClass;
    private static Class<?> animsSatsujinClass;
    private static Object weaponPassiveSlot;
    private static Object[] allSlots;
    private static Object sheathKeyHolder;

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

            sheathKeyHolder = Class.forName("reascer.wom.skill.WOMSkillDataKeys").getField("SHEATH").get(null);
            animsSatsujinClass = Class.forName("reascer.wom.gameasset.animations.weapons.AnimsSatsujin");

            state = 1;
            SheathToggle.LOGGER.info("[sheathtoggle] Epic Fight + Weapons of Miracles encontrados.");
        } catch (Throwable t) {
            state = -1;
            SheathToggle.LOGGER.warn("[sheathtoggle] Epic Fight/Weapons of Miracles indisponiveis ou com nomes diferentes; o mod ficara inerte. Motivo: {}", t.toString());
        }
        return state == 1;
    }

    // ---------------------------------------------------------------- API

    /** Zera o timer de auto-guardar (se o jogador estiver com a passiva da Satsujin ativa). */
    public static void suppressAutoSheath(ServerPlayer player) {
        if (!init()) return;
        try {
            Object patch = getPatch(player);
            if (patch == null) return;
            Object container = findSatsujinContainer(patch);
            if (container == null) return;
            Object skill = call(container, "getSkill");
            call(skill, "setConsumption", container, 0.0F);
        } catch (Throwable t) {
            warnRuntime("suppressAutoSheath", t);
        }
    }

    /** Alterna guardar/sacar. Retorna true se alternou. */
    public static boolean toggleSheath(ServerPlayer player) {
        if (!init()) return false;
        try {
            Object patch = getPatch(player);
            if (patch == null) return false;
            Object container = findSatsujinContainer(patch);
            if (container == null) return false; // nao esta com a Satsujin na mao

            Object entityState = call(patch, "getEntityState");
            if ((Boolean) call(entityState, "inaction")) return false; // no meio de uma animacao

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
        } catch (Throwable t) {
            warnRuntime("toggleSheath", t);
            return false;
        }
    }

    // ---------------------------------------------------------------- helpers

    private static Object getPatch(ServerPlayer player) throws Exception {
        return callStatic(capsClass, "getEntityPatch", player, serverPlayerPatchClass);
    }

    /** Acha o SkillContainer cuja skill e a SatsujinPassive (slot WEAPON_PASSIVE primeiro). */
    private static Object findSatsujinContainer(Object patch) {
        Object c = containerIfSatsujin(patch, weaponPassiveSlot);
        if (c != null) return c;
        for (Object slot : allSlots) {
            if (slot == weaponPassiveSlot) continue;
            c = containerIfSatsujin(patch, slot);
            if (c != null) return c;
        }
        return null;
    }

    private static Object containerIfSatsujin(Object patch, Object slot) {
        try {
            Object container = call(patch, "getSkill", slot);
            if (container == null) return null;
            Object skill = call(container, "getSkill");
            if (skill != null && skill.getClass().getName().equals(SATSUJIN_PASSIVE)) return container;
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
