package com.familyquest.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import org.jetbrains.compose.resources.painterResource
import com.familyquest.app.generated.resources.Res
import com.familyquest.app.generated.resources.icon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.familyquest.app.ui.theme.AppBackground
import com.familyquest.app.ui.theme.CoinGold
import com.familyquest.app.ui.theme.MagicPurple
import kotlinx.coroutines.delay

private data class OnboardingSlide(
    val emoji: String,
    val color: Color,
    val title: String,
    val body: String,
    val hint: String,
)

private val onboardingSlides = listOf(
    OnboardingSlide(
        emoji = "⚔️",
        color = Color(0xFFFF6B6B),
        title = "Daily Quest System",
        body = "Set goals. Earn coins. Build streaks. Repeat.",
        hint = "Custom deadlines + flexible schedules.",
    ),
    OnboardingSlide(
        emoji = "🪙",
        color = CoinGold,
        title = "Coins = Motivation",
        body = "Every completed quest earns coins, making your progress visible and rewarding.",
        hint = "Complete more. Earn faster.",
    ),
    OnboardingSlide(
        emoji = "🛍️",
        color = Color(0xFF4ECDC4),
        title = "Reward Store",
        body = "Spend your coins on real-life rewards that make the journey worthwhile.",
        hint = "Create and customize your own rewards.",
    ),
    OnboardingSlide(
        emoji = "⭐",
        color = Color(0xFFA78BFA),
        title = "Wish Goal Tracker",
        body = "Tap ⭐ in the store to pin a wish goal to your quest board — always knowing how close you are.",
        hint = "Let your goal be the fuel that drives you",
    ),
)

@Composable
fun FirstRunExperience(onComplete: () -> Unit) {
    var showLanding by rememberSaveable { mutableStateOf(true) }
    if (showLanding) {
        LandingScreen(onNext = { showLanding = false })
    } else {
        OnboardingScreen(onComplete = onComplete)
    }
}

@Composable
private fun LandingScreen(onNext: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF2D1B6B), AppBackground),
                ),
            )
            .testTag("first-run-landing"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 32.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(RoundedCornerShape(34.dp))
                        .background(Color(0xFF17152A)),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(Res.drawable.icon),
                        contentDescription = "Quest and Reward icon",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "Level Up Your Life",
                    color = CoinGold,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.sp,
                    maxLines = 1,
                    softWrap = false,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("first-run-headline"),
                )
                Text(
                    text = "QUESTREWARD",
                    color = MagicPurple.copy(alpha = 0.88f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.sp,
                )
                Spacer(Modifier.height(28.dp))
                Text(
                    text = "Every quest is a power-up.",
                    color = Color.White.copy(alpha = 0.90f),
                    fontSize = 21.sp,
                    lineHeight = 30.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    letterSpacing = 0.sp,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Complete small goals, earn coins, and unlock real rewards.",
                    color = Color.White.copy(alpha = 0.42f),
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 260.dp),
                    letterSpacing = 0.sp,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Your life is the game. Start playing.",
                    color = Color.White.copy(alpha = 0.64f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    letterSpacing = 0.sp,
                )
            }
            Spacer(Modifier.weight(1f))
            GradientActionButton(
                label = "Start Exploring",
                testTag = "first-run-start",
                color = MagicPurple,
                onClick = onNext,
                icon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                },
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Self-discipline is the greatest gift you can give yourself",
                color = Color.White.copy(alpha = 0.25f),
                fontSize = 12.sp,
                letterSpacing = 0.sp,
            )
        }
    }
}

@Composable
private fun OnboardingScreen(onComplete: () -> Unit) {
    var step by rememberSaveable { mutableStateOf(0) }
    val displayedStep = step
    val slide = onboardingSlides[displayedStep]
    val isLastStep = displayedStep == onboardingSlides.lastIndex

    LaunchedEffect(step) {
        val expectedStep = step
        if (expectedStep < onboardingSlides.lastIndex) {
            delay(4_500)
            if (step == expectedStep) {
                step = (expectedStep + 1).coerceAtMost(onboardingSlides.lastIndex)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF1A1040), AppBackground),
                ),
            )
            .testTag("first-run-onboarding"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .testTag("first-run-step-${step + 1}"),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = onComplete,
                    modifier = Modifier.testTag("first-run-skip"),
                ) {
                    Text("Skip", color = Color.White.copy(alpha = 0.45f), letterSpacing = 0.sp)
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(128.dp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(slide.color.copy(alpha = 0.34f), Color.Transparent),
                            ),
                            CircleShape,
                        )
                        .semantics { contentDescription = "${slide.title} icon" },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(slide.emoji, fontSize = 64.sp, letterSpacing = 0.sp)
                }
                Spacer(Modifier.height(28.dp))
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(slide.color.copy(alpha = 0.14f))
                        .padding(horizontal = 13.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = "${step + 1} / ${onboardingSlides.size}",
                        color = slide.color,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.sp,
                    )
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    text = slide.title,
                    color = Color.White,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    letterSpacing = 0.sp,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = slide.body,
                    color = Color.White.copy(alpha = 0.58f),
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 300.dp),
                    letterSpacing = 0.sp,
                )
                Spacer(Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(slide.color.copy(alpha = 0.08f))
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                ) {
                    Text(
                        text = "💡 ${slide.hint}",
                        color = slide.color,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        letterSpacing = 0.sp,
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                onboardingSlides.indices.forEach { index ->
                    val selected = index == step
                    Box(
                        modifier = Modifier
                            .size(width = if (selected) 20.dp else 8.dp, height = 8.dp)
                            .clip(CircleShape)
                            .background(if (selected) slide.color else Color.White.copy(alpha = 0.18f))
                            .selectable(
                                selected = selected,
                                role = Role.RadioButton,
                                onClick = { step = index },
                            )
                            .testTag("first-run-dot-${index + 1}")
                            .semantics { contentDescription = "Onboarding step ${index + 1}" },
                    )
                }
            }
            Spacer(Modifier.height(22.dp))
            GradientActionButton(
                label = if (isLastStep) "Let's Go 🎉" else "Next",
                testTag = if (isLastStep) "first-run-finish" else "first-run-next",
                color = slide.color,
                onClick = {
                    if (isLastStep) {
                        onComplete()
                    } else if (step == displayedStep) {
                        step = (displayedStep + 1).coerceAtMost(onboardingSlides.lastIndex)
                    }
                },
                icon = {
                    Icon(
                        imageVector = if (isLastStep) Icons.Default.Check else Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                },
            )
            Spacer(Modifier.height(36.dp))
        }
    }
}

@Composable
private fun GradientActionButton(
    label: String,
    testTag: String,
    color: Color,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(color, color.copy(alpha = 0.76f)),
                ),
            )
            .clickable(role = Role.Button, onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.sp,
            )
            Spacer(Modifier.width(8.dp))
            icon()
        }
    }
}
