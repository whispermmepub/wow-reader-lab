package com.whisper.wowreader;

import android.app.Activity;
import android.content.MutableContextWrapper;
import android.net.Uri;
import android.os.CancellationSignal;
import android.os.Bundle;

import androidx.credentials.ClearCredentialStateRequest;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.ClearCredentialException;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.credentials.exceptions.NoCredentialException;

import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

import java.util.concurrent.Executor;

/**
 * Handles identity only. Google Drive permission is requested separately by
 * {@link GoogleDriveSync} after Firebase sign-in succeeds.
 */
final class GoogleAccountAuth {
    interface Callback {
        void onReady(GoogleDriveSync.Profile profile);
        void onError(String message);
    }

    private final Activity activity;
    private final FirebaseAuth firebaseAuth;
    private final CredentialManager credentialManager;
    private final Executor mainExecutor;

    GoogleAccountAuth(Activity activity) {
        this.activity = activity;
        firebaseAuth = FirebaseAuth.getInstance();
        credentialManager = CredentialManager.create(activity.getApplicationContext());
        mainExecutor = command -> activity.runOnUiThread(command);
    }

    GoogleDriveSync.Profile currentProfile() {
        return profileOf(firebaseAuth.getCurrentUser());
    }

    boolean isSignedIn() {
        return firebaseAuth.getCurrentUser() != null;
    }

    void signIn(boolean chooseAccount, Callback callback) {
        if (chooseAccount) {
            requestButtonCredential(callback);
        } else {
            requestCredential(true, true, callback);
        }
    }

    private void requestButtonCredential(Callback callback) {
        GetSignInWithGoogleOption option = new GetSignInWithGoogleOption.Builder(
                activity.getString(R.string.default_web_client_id))
                .build();
        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(option)
                .build();
        launchCredentialRequest(request, false, callback);
    }

    private void requestCredential(boolean authorizedOnly, boolean allowRetry, Callback callback) {
        GetGoogleIdOption option = new GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(authorizedOnly)
                .setAutoSelectEnabled(authorizedOnly)
                .setServerClientId(activity.getString(R.string.default_web_client_id))
                .build();
        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(option)
                .build();
        launchCredentialRequest(request, authorizedOnly && allowRetry, callback);
    }

    private void launchCredentialRequest(GetCredentialRequest request,
                                         boolean retryWithAllAccounts,
                                         Callback callback) {
        credentialManager.getCredentialAsync(
                new MutableContextWrapper(activity),
                request,
                new CancellationSignal(),
                mainExecutor,
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override public void onResult(GetCredentialResponse result) {
                        handleCredential(result == null ? null : result.getCredential(), callback);
                    }

                    @Override public void onError(GetCredentialException error) {
                        if (retryWithAllAccounts) {
                            requestCredential(false, false, callback);
                            return;
                        }
                        callback.onError(friendly(error, "Google sign-in was not completed"));
                    }
                });
    }

    private void handleCredential(Credential credential, Callback callback) {
        if (!(credential instanceof CustomCredential) ||
                !GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(credential.getType())) {
            callback.onError("Google sign-in returned an unsupported credential");
            return;
        }
        try {
            Bundle data = ((CustomCredential) credential).getData();
            GoogleIdTokenCredential google = GoogleIdTokenCredential.createFrom(data);
            AuthCredential firebaseCredential =
                    GoogleAuthProvider.getCredential(google.getIdToken(), null);
            firebaseAuth.signInWithCredential(firebaseCredential)
                    .addOnCompleteListener(activity, task -> {
                        if (!task.isSuccessful()) {
                            callback.onError(friendly(task.getException(), "Firebase sign-in failed"));
                            return;
                        }
                        GoogleDriveSync.Profile profile = profileOf(firebaseAuth.getCurrentUser());
                        if (profile == null) {
                            callback.onError("Google profile is unavailable");
                            return;
                        }
                        callback.onReady(profile);
                    });
        } catch (Exception error) {
            callback.onError(friendly(error, "Google sign-in response was invalid"));
        }
    }

    void signOut(Runnable onDone) {
        firebaseAuth.signOut();
        credentialManager.clearCredentialStateAsync(
                new ClearCredentialStateRequest(),
                new CancellationSignal(),
                mainExecutor,
                new CredentialManagerCallback<Void, ClearCredentialException>() {
                    @Override public void onResult(Void result) {
                        if (onDone != null) onDone.run();
                    }

                    @Override public void onError(ClearCredentialException error) {
                        if (onDone != null) onDone.run();
                    }
                });
    }

    private static GoogleDriveSync.Profile profileOf(FirebaseUser user) {
        if (user == null) return null;
        GoogleDriveSync.Profile profile = new GoogleDriveSync.Profile();
        profile.uid = value(user.getUid());
        profile.name = value(user.getDisplayName());
        profile.email = value(user.getEmail());
        Uri photo = user.getPhotoUrl();
        profile.picture = photo == null ? "" : photo.toString();
        if (profile.name.isEmpty()) {
            int at = profile.email.indexOf('@');
            profile.name = at > 0 ? profile.email.substring(0, at) : "Google account";
        }
        return profile;
    }

    private static String value(String value) {
        return value == null ? "" : value.trim();
    }

    private static String friendly(Throwable error, String fallback) {
        if (error == null) return fallback;
        if (error instanceof NoCredentialException) {
            return "No Google account was available. Add a Google account or update Google Play services, then try again.";
        }
        String message = error.getLocalizedMessage();
        return message == null || message.trim().isEmpty() ? fallback : message.trim();
    }
}
