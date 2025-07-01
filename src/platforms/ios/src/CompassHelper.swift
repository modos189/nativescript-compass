import Foundation
import CoreLocation

@objc public class CompassHelper: NSObject, CLLocationManagerDelegate {

    public typealias CompassCompletion = (Double, Double, Double, Double, Double, String?) -> Void

    private static var sharedInstance: CompassHelper?
    private var locationManager: CLLocationManager
    private var completion: CompassCompletion?

    // Configuration
    private var minChangeThreshold: Double = 3.0
    private var updateThrottle: TimeInterval = 0.2 // 200ms
    private var filter: Double = 0.8
    private var usesTrueHeading: Bool = false

    // State
    private var lastCallbackTime: TimeInterval = 0
    private var updateTimer: Timer?
    private var isUpdating: Bool = false

    public override init() {
        locationManager = CLLocationManager()
        super.init()
        locationManager.delegate = self
    }

    @objc public static func isCompassAvailable() -> Bool {
        return CLLocationManager.headingAvailable()
    }

    @objc public static func startUpdatingWithOptionsCompletion(
        _ minChangeThreshold: Double,
        _ updateThrottle: Double,
        _ filter: Double,
        _ usesTrueHeading: Bool,
        _ headingFilter: Double,
        _ completion: @escaping CompassCompletion
    ) -> Bool {

        guard CLLocationManager.headingAvailable() else {
            completion(0, 0, 0, 0, 0, "Compass not available on this device")
            return false
        }

        // Create or get shared instance
        if sharedInstance == nil {
            sharedInstance = CompassHelper()
        }

        guard let compass = sharedInstance else {
            completion(0, 0, 0, 0, 0, "Failed to create compass instance")
            return false
        }

        // Configure compass
        compass.minChangeThreshold = minChangeThreshold
        compass.updateThrottle = updateThrottle / 1000.0 // Convert ms to seconds
        compass.filter = filter
        compass.usesTrueHeading = usesTrueHeading
        compass.completion = completion

        // Check and request permissions
        let authStatus = CLLocationManager.authorizationStatus()
        if authStatus == .notDetermined {
            compass.locationManager.requestWhenInUseAuthorization()
            return false // Will retry after permission is granted
        } else if authStatus == .denied || authStatus == .restricted {
            completion(0, 0, 0, 0, 0, "Location permission denied")
            return false
        }

        // Configure location manager
        // Use provided headingFilter or default to minChangeThreshold
        let actualHeadingFilter = headingFilter > 0 ? headingFilter : minChangeThreshold
        compass.locationManager.headingFilter = actualHeadingFilter
        compass.locationManager.headingOrientation = .portrait

        // Start heading updates
        compass.locationManager.startUpdatingHeading()
        compass.isUpdating = true

        // Start throttling timer
        compass.startThrottleTimer()

        return true
    }

    @objc public static func stopUpdating() -> Bool {
        guard let compass = sharedInstance else { return true }

        compass.locationManager.stopUpdatingHeading()
        compass.stopThrottleTimer()
        compass.isUpdating = false
        compass.completion = nil

        return true
    }

    @objc public static func getCurrentReadingWithOptionsCompletion(
        _ usesTrueHeading: Bool,
        _ completion: @escaping CompassCompletion
    ) {

        guard CLLocationManager.headingAvailable() else {
            completion(0, 0, 0, 0, 0, "Compass not available on this device")
            return
        }

        // Create temporary compass for single reading
        let compass = CompassHelper()
        compass.usesTrueHeading = usesTrueHeading

        // Check permissions
        let authStatus = CLLocationManager.authorizationStatus()
        if authStatus == .denied || authStatus == .restricted {
            completion(0, 0, 0, 0, 0, "Location permission denied")
            return
        }

        // Set up single reading timeout
        let timeoutTimer = Timer.scheduledTimer(withTimeInterval: 3.0, repeats: false) { _ in
            compass.locationManager.stopUpdatingHeading()
            completion(0, 0, 0, 0, 0, "Timeout getting compass reading")
        }

        // Set completion that cancels timeout
        compass.completion = { heading, accuracy, magneticHeading, trueHeading, timestamp, error in
            timeoutTimer.invalidate()
            compass.locationManager.stopUpdatingHeading()
            completion(heading, accuracy, magneticHeading, trueHeading, timestamp, error)
        }

        // Start heading updates
        compass.locationManager.headingFilter = 1.0 // High sensitivity for single reading
        compass.locationManager.startUpdatingHeading()
    }

    private func startThrottleTimer() {
        updateTimer = Timer.scheduledTimer(withTimeInterval: updateThrottle, repeats: true) { [weak self] _ in
            self?.checkAndSendReading()
        }
    }

    private func stopThrottleTimer() {
        updateTimer?.invalidate()
        updateTimer = nil
    }

    private var lastReading: CLHeading?

    private func checkAndSendReading() {
        guard let heading = lastReading,
              let completion = completion else { return }

        // Check if reading is fresh (avoid processing old data)
        let headingAge = Date().timeIntervalSince(heading.timestamp)
        if headingAge > 0.5 { return } // Skip readings older than 500ms

        let currentHeading = usesTrueHeading && heading.trueHeading >= 0 ? heading.trueHeading : heading.magneticHeading

        let accuracy = heading.headingAccuracy >= 0 ? heading.headingAccuracy : 15.0
        let timestamp = heading.timestamp.timeIntervalSince1970 * 1000

        completion(
            currentHeading,
            accuracy,
            heading.magneticHeading,
            heading.trueHeading >= 0 ? heading.trueHeading : heading.magneticHeading,
            timestamp,
            nil
        )

        lastCallbackTime = Date().timeIntervalSince1970
    }

    // MARK: - CLLocationManagerDelegate

    public func locationManager(_ manager: CLLocationManager, didUpdateHeading newHeading: CLHeading) {
        lastReading = newHeading

        // For single readings (getCurrentReading), send immediately
        if !isUpdating && completion != nil {
            let currentHeading = usesTrueHeading && newHeading.trueHeading >= 0 ? newHeading.trueHeading : newHeading.magneticHeading
            let accuracy = newHeading.headingAccuracy >= 0 ? newHeading.headingAccuracy : 15.0
            let timestamp = newHeading.timestamp.timeIntervalSince1970 * 1000

            completion?(
                currentHeading,
                accuracy,
                newHeading.magneticHeading,
                newHeading.trueHeading >= 0 ? newHeading.trueHeading : newHeading.magneticHeading,
                timestamp,
                nil
            )
        }
    }

    public func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        completion?(0, 0, 0, 0, 0, "Compass error: \(error.localizedDescription)")
    }

    public func locationManager(_ manager: CLLocationManager, didChangeAuthorization status: CLAuthorizationStatus) {
        if status == .authorizedWhenInUse || status == .authorizedAlways {
            // Permission granted, can start compass if requested
        } else if status == .denied || status == .restricted {
            completion?(0, 0, 0, 0, 0, "Location permission denied")
        }
    }
}
