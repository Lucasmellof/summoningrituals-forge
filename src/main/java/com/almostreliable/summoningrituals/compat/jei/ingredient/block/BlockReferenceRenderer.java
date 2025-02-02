package com.almostreliable.summoningrituals.compat.jei.ingredient.block;

import com.almostreliable.summoningrituals.Constants;
import com.almostreliable.summoningrituals.recipe.component.BlockReference;
import com.almostreliable.summoningrituals.util.Bruhtils;
import com.almostreliable.summoningrituals.util.TextUtils;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Vector3f;
import mezz.jei.api.ingredients.IIngredientRenderer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.EmptyModelData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class BlockReferenceRenderer implements IIngredientRenderer<BlockReference> {

    private final Minecraft mc;
    private final BlockRenderDispatcher blockRenderer;
    private final int size;

    public BlockReferenceRenderer(int size) {
        mc = Minecraft.getInstance();
        blockRenderer = mc.getBlockRenderer();
        this.size = size;
    }

    @Override
    public void render(PoseStack stack, @Nullable BlockReference blockReference) {
        if (blockReference == null) return;
        stack.pushPose();
        {
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.defaultBlendFunc();

            stack.translate(0.93f * size, 0.77f * size, 100);
            stack.scale(0.625f * size, 0.625f * size, 0.625f * size);
            stack.mulPose(Vector3f.ZN.rotationDegrees(180));
            stack.mulPose(Vector3f.XN.rotationDegrees(30));
            stack.mulPose(Vector3f.YP.rotationDegrees(45));
            var bufferSource = mc.renderBuffers().bufferSource();
            blockRenderer.renderSingleBlock(
                blockReference.getDisplayState(),
                stack,
                bufferSource,
                LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY,
                EmptyModelData.INSTANCE
            );
            bufferSource.endBatch();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
        }
        stack.popPose();
    }

    @Override
    public List<Component> getTooltip(BlockReference blockReference, TooltipFlag tooltipFlag) {
        var displayState = blockReference.getDisplayState();
        var stack = new ItemStack(displayState.getBlock());
        try {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(
                TextUtils.translate(Constants.TOOLTIP, Constants.BLOCK_BELOW, ChatFormatting.GOLD)
                    .append(": ")
                    .append(((MutableComponent) stack.getHoverName()).withStyle(ChatFormatting.WHITE))
            );
            if (tooltipFlag.isAdvanced()) {
                tooltip.add(new TextComponent(Bruhtils.getId(stack.getItem())
                    .toString()).withStyle(ChatFormatting.DARK_GRAY));
            }
            appendStateTooltip(displayState, tooltip);
            return tooltip;
        } catch (Exception e) {
            return List.of(new TextComponent("Error rendering tooltip!").append(e.getMessage())
                .withStyle(ChatFormatting.DARK_RED));
        }
    }

    @Override
    public int getWidth() {
        return size;
    }

    @Override
    public int getHeight() {
        return size;
    }

    private void appendStateTooltip(BlockState displayState, List<Component> tooltip) {
        var defaultState = displayState.getBlock().defaultBlockState();
        List<String> modifiedProps = new ArrayList<>();
        for (var property : displayState.getProperties()) {
            if (!displayState.getValue(property).equals(defaultState.getValue(property))) {
                modifiedProps.add(property.getName() + ": " + displayState.getValue(property));
            }
        }
        if (modifiedProps.isEmpty()) return;

        tooltip.add(TextUtils.translate(Constants.TOOLTIP, Constants.PROPERTIES, ChatFormatting.AQUA)
            .append(TextUtils.colorize(":", ChatFormatting.AQUA)));
        for (var prop : modifiedProps) {
            tooltip.add(TextUtils.colorize("» ", ChatFormatting.GRAY)
                .append(TextUtils.colorize(prop, ChatFormatting.WHITE)));
        }
    }
}
