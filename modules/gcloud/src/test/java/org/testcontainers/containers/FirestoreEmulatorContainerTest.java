package org.testcontainers.containers;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import com.google.api.core.ApiFuture;
import com.google.cloud.NoCredentials;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.WriteResult;
import org.junit.Rule;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FirestoreEmulatorContainerTest {

	@Rule
	public FirestoreEmulatorContainer emulator = new FirestoreEmulatorContainer();

	@Test
	public void testSimple() throws ExecutionException, InterruptedException {
		FirestoreOptions options = FirestoreOptions.getDefaultInstance().toBuilder()
				.setHost(emulator.getContainerIpAddress() + ":" + emulator.getMappedPort(8080))
				.setCredentials(NoCredentials.getInstance())
				.build();
		Firestore firestore = options.getService();

		CollectionReference users = firestore.collection("users");
		DocumentReference docRef = users.document("alovelace");
		Map<String, Object> data = new HashMap<>();
		data.put("first", "Ada");
		data.put("last", "Lovelace");
		ApiFuture<WriteResult> result = docRef.set(data);

		System.out.println(result.get().getUpdateTime());

		ApiFuture<QuerySnapshot> query = users.get();
		QuerySnapshot querySnapshot = query.get();

		assertThat(querySnapshot.getDocuments().get(0).getData()).containsEntry("first", "Ada");
	}

}
