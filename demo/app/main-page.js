import { Observable } from "@nativescript/core";

let Compass;
try {
  // Try to import from local src first (for development)
  Compass = require("../../src/nativescript-compass").Compass;
} catch (error) {
  console.error("Failed to import Compass:", error);
}

let viewModel;
let page;
let compassStarted = false;

export function onLoaded(args) {
  page = args.object;
  viewModel = new Observable();

  // Initialize view model
  viewModel.set("headingText", "Heading: --°");
  viewModel.set("accuracyText", "Accuracy: --°");
  viewModel.set("startButtonText", "Start Compass");
  viewModel.set("statusText", "Tap 'Start Compass' to begin");

  page.bindingContext = viewModel;

  // Check if compass is available
  if (!Compass || !Compass.isAvailable()) {
    viewModel.set("statusText", "❌ Compass not available on this device");
    viewModel.set("startButtonText", "Not Available");
  }
}

export function onUnloaded() {
  // Stop compass when leaving page
  if (compassStarted && Compass) {
    Compass.stopUpdating();
    compassStarted = false;
  }
}

export async function toggleCompass() {
  if (!Compass || !Compass.isAvailable()) {
    viewModel.set("statusText", "❌ Compass not available on this device");
    return;
  }

  try {
    if (compassStarted) {
      // Stop compass
      const stopped = Compass.stopUpdating();
      if (stopped) {
        compassStarted = false;
        viewModel.set("startButtonText", "Start Compass");
        viewModel.set("statusText", "Compass stopped");
        updateNeedle(0); // Reset needle
      }
    } else {
      // Start compass
      viewModel.set("statusText", "Starting compass...");

      const options = {
        minChangeThreshold: 3, // Trigger on 3° change
        updateThrottle: 200, // Update max every 200ms
        filter: 0.8, // Smooth filtering
        android: {
          useSensorFusion: true, // Use gyroscope if available
          // sensorDelay: 'game', // 'fastest', 'game', 'ui', 'normal' (default: 'ui')
        },
        ios: {
          usesTrueHeading: false, // Use magnetic north
          // headingFilter: 1.0, // Hardware updates every 1° (default: 3°)
        },
      };

      const started = await Compass.startUpdating(
        options,
        onCompassReading,
        onCompassError
      );

      if (started) {
        compassStarted = true;
        viewModel.set("startButtonText", "Stop Compass");
        viewModel.set(
          "statusText",
          "✅ Compass running (move device to see changes)"
        );
      } else {
        viewModel.set("statusText", "❌ Failed to start compass");
      }
    }
  } catch (error) {
    console.error("Compass error:", error);
    viewModel.set("statusText", `❌ Error: ${error.message || error}`);
  }
}

export async function getSingleReading() {
  if (!Compass || !Compass.isAvailable()) {
    viewModel.set("statusText", "❌ Compass not available on this device");
    return;
  }

  try {
    viewModel.set("statusText", "Getting single reading...");

    const reading = await Compass.getCurrentReading();

    viewModel.set("headingText", `Heading: ${reading.heading.toFixed(1)}°`);
    viewModel.set("accuracyText", `Accuracy: ±${reading.accuracy.toFixed(1)}°`);
    viewModel.set(
      "statusText",
      `📍 Single reading obtained at ${new Date(
        reading.timestamp
      ).toLocaleTimeString()}`
    );

    updateNeedle(reading.heading);
  } catch (error) {
    console.error("Single reading error:", error);
    viewModel.set(
      "statusText",
      `❌ Failed to get reading: ${error.message || error}`
    );
  }
}

function onCompassReading(reading) {
  // Update displayed values
  viewModel.set("headingText", `Heading: ${reading.heading.toFixed(1)}°`);
  viewModel.set("accuracyText", `Accuracy: ±${reading.accuracy.toFixed(1)}°`);

  // Update needle rotation
  updateNeedle(reading.heading);

  // Show additional info if available
  let additionalInfo = "";
  if (
    reading.magneticHeading !== undefined &&
    Math.abs(reading.magneticHeading - reading.heading) > 1
  ) {
    additionalInfo += ` | Magnetic: ${reading.magneticHeading.toFixed(1)}°`;
  }
  if (
    reading.trueHeading !== undefined &&
    Math.abs(reading.trueHeading - reading.heading) > 1
  ) {
    additionalInfo += ` | True: ${reading.trueHeading.toFixed(1)}°`;
  }

  const statusText = `✅ Compass active${additionalInfo}`;
  viewModel.set("statusText", statusText);
}

function onCompassError(error) {
  console.error("Compass reading error:", error);
  viewModel.set("statusText", `❌ Compass error: ${error}`);
}

let needleAnimation = null;
let targetNeedlePosition = 0;
let animationStartTime = 0;
let animationStartPosition = 0;
let animationTargetPosition = 0;

function updateNeedle(heading) {
  try {
    const needle = page.getViewById("needle");
    if (needle) {
      const targetRotation = -heading;
      const now = Date.now();

      // If animation is running, cancel it and set needle to its intended final position
      if (needleAnimation) {
        needleAnimation = null; // Stop manual animation
        needle.rotate = targetNeedlePosition;
      }

      const currentRotation = needle.rotate || 0;

      // Calculate shortest path considering 360°/0° boundary
      let deltaRotation = targetRotation - currentRotation;
      if (deltaRotation > 180) {
        deltaRotation -= 360;
      } else if (deltaRotation < -180) {
        deltaRotation += 360;
      }

      const finalRotation = currentRotation + deltaRotation;
      targetNeedlePosition = finalRotation;

      // Start manual animation (system-independent)
      animationStartTime = now;
      animationStartPosition = currentRotation;
      animationTargetPosition = finalRotation;
      needleAnimation = true;

      // Start animation loop
      animateNeedleManually();
    }
  } catch (error) {
    console.error("Error updating needle:", error);
  }
}

function animateNeedleManually() {
  if (!needleAnimation) return;

  const needle = page.getViewById("needle");
  if (!needle) {
    needleAnimation = null;
    return;
  }

  const now = Date.now();
  const elapsed = now - animationStartTime;
  const duration = 200;

  if (elapsed >= duration) {
    needle.rotate = animationTargetPosition;
    targetNeedlePosition = animationTargetPosition;
    needleAnimation = null;
    return;
  }

  // Calculate current position using linear interpolation
  const progress = elapsed / duration;
  const currentPosition =
    animationStartPosition +
    (animationTargetPosition - animationStartPosition) * progress;

  // Update needle position
  needle.rotate = currentPosition;

  // Schedule next frame (~16ms for 60fps)
  setTimeout(() => animateNeedleManually(), 16);
}
