package com.almostreliable.summoningrituals.recipe.component;

import com.almostreliable.summoningrituals.Constants;
import com.almostreliable.summoningrituals.util.Bruhtils;
import com.almostreliable.summoningrituals.util.GameUtils;
import com.almostreliable.summoningrituals.util.MathUtils;
import com.almostreliable.summoningrituals.util.SerializeUtils;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.util.TriConsumer;

import java.util.ArrayList;
import java.util.Objects;
import java.util.Random;

public final class RecipeOutputs {

    private static final Vec3i DEFAULT_OFFSET = new Vec3i(0, 2, 0);
    private static final Vec3i DEFAULT_SPREAD = new Vec3i(1, 0, 1);
    private static final Random RANDOM = new Random();

    private final NonNullList<RecipeOutput<?>> outputs;

    private RecipeOutputs(NonNullList<RecipeOutput<?>> outputs) {
        this.outputs = outputs;
    }

    public RecipeOutputs() {
        this(NonNullList.create());
    }

    public static RecipeOutputs fromJson(JsonArray json) {
        NonNullList<RecipeOutput<?>> recipeOutputs = NonNullList.create();
        for (var output : json) {
            recipeOutputs.add(RecipeOutput.fromJson(output.getAsJsonObject()));
        }
        return new RecipeOutputs(recipeOutputs);
    }

    public static RecipeOutputs fromNetwork(FriendlyByteBuf buffer) {
        var length = buffer.readVarInt();
        NonNullList<RecipeOutput<?>> outputs = NonNullList.create();
        for (var i = 0; i < length; i++) {
            outputs.add(RecipeOutput.fromNetwork(buffer));
        }
        return new RecipeOutputs(outputs);
    }

    public JsonArray toJson() {
        JsonArray json = new JsonArray();
        for (var output : outputs) {
            json.add(output.toJson());
        }
        return json;
    }

    public void toNetwork(FriendlyByteBuf buffer) {
        buffer.writeVarInt(outputs.size());
        for (var output : outputs) {
            output.toNetwork(buffer);
        }
    }

    public void add(RecipeOutput<?> output) {
        outputs.add(output);
    }

    public void handleRecipe(ServerLevel level, BlockPos origin) {
        for (var output : outputs) {
            output.spawn(level, origin);
        }
    }

    public int size() {
        return outputs.size();
    }

    public void forEach(TriConsumer<OutputType, RecipeOutput<?>, Integer> consumer) {
        for (var i = 0; i < outputs.size(); i++) {
            var output = outputs.get(i);
            consumer.accept(output.getType(), output, i);
        }
    }

    public enum OutputType {
        ITEM, MOB
    }

    public abstract static class RecipeOutput<T> {

        final T output;
        private final OutputType type;
        CompoundTag data;
        Vec3i offset = DEFAULT_OFFSET;
        Vec3i spread = DEFAULT_SPREAD;

        private RecipeOutput(OutputType type, T output) {
            this.type = type;
            this.output = output;
            data = new CompoundTag();
        }

        private static RecipeOutput<?> fromJson(JsonObject json) {
            RecipeOutput<?> output;
            if (json.has(Constants.ITEM)) {
                output = ItemOutput.fromJson(json);
            } else if (json.has(Constants.MOB)) {
                output = MobOutput.fromJson(json);
            } else {
                throw new IllegalArgumentException("Invalid recipe output");
            }
            if (json.has(Constants.DATA)) {
                output.data = SerializeUtils.nbtFromString(GsonHelper.getAsString(json, Constants.DATA));
            }
            if (output.getCount() > 1) {
                if (json.has(Constants.OFFSET)) {
                    output.offset = SerializeUtils.vec3FromJson(json.getAsJsonObject(Constants.OFFSET));
                }
                if (json.has(Constants.SPREAD)) {
                    output.spread = SerializeUtils.vec3FromJson(json.getAsJsonObject(Constants.SPREAD));
                }
            }

            return output;
        }

        private static RecipeOutput<?> fromNetwork(FriendlyByteBuf buffer) {
            RecipeOutput<?> output;
            var i = buffer.readVarInt();
            if (i == 0) {
                output = ItemOutput.fromNetwork(buffer);
            } else if (i == 1) {
                output = MobOutput.fromNetwork(buffer);
            } else {
                throw new IllegalArgumentException("Invalid recipe output");
            }

            if (buffer.readBoolean()) {
                output.data = buffer.readNbt();
            }
            if (output.getCount() > 1) {
                output.offset = SerializeUtils.vec3FromNetwork(buffer);
                output.spread = SerializeUtils.vec3FromNetwork(buffer);
            }

            return output;
        }

        abstract JsonObject toJson();

        void writeJsonDefaults(JsonObject json) {
            if (!data.isEmpty()) {
                json.addProperty(Constants.DATA, data.toString());
            }
            if (getCount() > 1) {
                if (!offset.equals(DEFAULT_OFFSET)) {
                    json.add(Constants.OFFSET, SerializeUtils.vec3ToJson(offset));
                }
                if (!spread.equals(DEFAULT_SPREAD)) {
                    json.add(Constants.SPREAD, SerializeUtils.vec3ToJson(spread));
                }
            }
        }

        void toNetwork(FriendlyByteBuf buffer) {
            if (data.isEmpty()) {
                buffer.writeBoolean(false);
            } else {
                buffer.writeBoolean(true);
                buffer.writeNbt(data);
            }
            if (getCount() > 1) {
                SerializeUtils.vec3ToNetwork(buffer, offset);
                SerializeUtils.vec3ToNetwork(buffer, spread);
            }
        }

        void writeDataToEntity(Entity entity) {
            if (data.isEmpty()) return;
            var entityData = entity.serializeNBT();
            for (var prop : data.getAllKeys()) {
                entityData.put(prop, Objects.requireNonNull(data.get(prop)));
            }
            entity.load(entityData);
        }

        Vec3 getRandomPos(BlockPos origin) {
            var x = spread.getX() > 0 ? RANDOM.nextDouble(-spread.getX(), spread.getX()) / 2.0 : 0;
            var y = spread.getY() > 0 ? RANDOM.nextDouble(-spread.getY(), spread.getY()) / 2.0 : 0;
            var z = spread.getZ() > 0 ? RANDOM.nextDouble(-spread.getZ(), spread.getZ()) / 2.0 : 0;
            return MathUtils.shiftToCenter(origin).add(MathUtils.vectorFromPos(offset)).add(x, y, z);
        }

        abstract void spawn(ServerLevel level, BlockPos origin);

        private OutputType getType() {
            return type;
        }

        public T getOutput() {
            return output;
        }

        public CompoundTag getData() {
            return data;
        }

        public abstract int getCount();
    }

    private static final class ItemOutput extends RecipeOutput<ItemStack> {

        private ItemOutput(ItemStack stack) {
            super(OutputType.ITEM, stack);
        }

        private static ItemOutput fromJson(JsonObject json) {
            var stack = ShapedRecipe.itemStackFromJson(json);
            return new ItemOutput(stack);
        }

        private static ItemOutput fromNetwork(FriendlyByteBuf buffer) {
            return new ItemOutput(buffer.readItem());
        }

        @Override
        JsonObject toJson() {
            var json = SerializeUtils.stackToJson(output);
            writeJsonDefaults(json);
            return json;
        }

        @Override
        void toNetwork(FriendlyByteBuf buffer) {
            buffer.writeVarInt(0);
            buffer.writeItem(output);
            super.toNetwork(buffer);
        }

        @Override
        void spawn(ServerLevel level, BlockPos origin) {
            var count = getCount();
            var stacks = new ArrayList<ItemStack>();
            while (count > 0) {
                var stack = output.copy();
                stack.setCount(Math.min(count, 4));
                stacks.add(stack);
                count -= stack.getCount();
            }

            for (var stack : stacks) {
                var pos = getRandomPos(origin);
                var itemEntity = new ItemEntity(level, pos.x, pos.y, pos.z, stack);
                writeDataToEntity(itemEntity);
                GameUtils.spawnEntity(level, itemEntity);
            }
        }

        @Override
        public int getCount() {
            return output.getCount();
        }
    }

    private static final class MobOutput extends RecipeOutput<EntityType<?>> {

        private final int count;

        private MobOutput(EntityType<?> mob, int count) {
            super(OutputType.MOB, mob);
            this.count = count;
        }

        private static MobOutput fromJson(JsonObject json) {
            var mob = SerializeUtils.mobFromJson(json);
            var count = GsonHelper.getAsInt(json, Constants.COUNT, 1);
            return new MobOutput(mob, count);
        }

        private static MobOutput fromNetwork(FriendlyByteBuf buffer) {
            var mob = SerializeUtils.mobFromNetwork(buffer);
            var count = buffer.readVarInt();
            return new MobOutput(mob, count);
        }

        @Override
        JsonObject toJson() {
            var json = new JsonObject();
            json.addProperty(Constants.MOB, Bruhtils.getId(output).toString());
            if (getCount() > 1) {
                json.addProperty(Constants.COUNT, getCount());
            }
            writeJsonDefaults(json);
            return json;
        }

        @Override
        void toNetwork(FriendlyByteBuf buffer) {
            buffer.writeVarInt(1);
            buffer.writeUtf(Bruhtils.getId(output).toString());
            buffer.writeVarInt(getCount());
            super.toNetwork(buffer);
        }

        @Override
        void spawn(ServerLevel level, BlockPos origin) {
            for (var i = 0; i < count; i++) {
                var mobEntity = output.create(level);
                if (mobEntity == null) return;
                writeDataToEntity(mobEntity);
                mobEntity.setPos(getRandomPos(origin));
                GameUtils.spawnEntity(level, mobEntity);
            }
        }

        @Override
        public int getCount() {
            return count;
        }
    }

    @SuppressWarnings("unused") // remapped by Rhino for Kube
    private abstract static class RecipeOutputBuilder {

        CompoundTag data;
        Vec3i offset;
        Vec3i spread;

        private RecipeOutputBuilder() {
            data = new CompoundTag();
            offset = new Vec3i(0, 2, 0);
            spread = new Vec3i(1, 0, 1);
        }

        public abstract RecipeOutput<?> build();

        public RecipeOutputBuilder data(CompoundTag data) {
            this.data = data;
            return this;
        }

        public RecipeOutputBuilder offset(int x, int y, int z) {
            this.offset = new Vec3i(x, y, z);
            return this;
        }

        public RecipeOutputBuilder spread(int x, int y, int z) {
            this.spread = new Vec3i(x, y, z);
            return this;
        }
    }

    @SuppressWarnings("unused") // remapped by Rhino for Kube
    public static class ItemOutputBuilder extends RecipeOutputBuilder {

        private ItemStack stack;

        public ItemOutputBuilder(ItemStack stack) {
            this.stack = stack;
        }

        public ItemOutputBuilder item(ItemStack item) {
            stack = item;
            return this;
        }

        @Override
        public ItemOutput build() {
            var output = new ItemOutput(stack);
            output.data = data;
            output.offset = offset;
            output.spread = spread;
            return output;
        }
    }

    @SuppressWarnings("unused") // remapped by Rhino for Kube
    public static class MobOutputBuilder extends RecipeOutputBuilder {

        private EntityType<?> mob;
        private int count;

        public MobOutputBuilder(EntityType<?> mob) {
            this.mob = mob;
            this.count = 1;
        }

        public MobOutputBuilder mob(EntityType<?> mob) {
            this.mob = mob;
            return this;
        }

        public MobOutputBuilder count(int count) {
            this.count = count;
            return this;
        }

        @Override
        public MobOutput build() {
            var output = new MobOutput(mob, count);
            output.data = data;
            output.offset = offset;
            output.spread = spread;
            return output;
        }
    }
}
