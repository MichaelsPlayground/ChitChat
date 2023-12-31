package com.bluej.chitchat.activities;

import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.bluej.chitchat.R;
import com.bluej.chitchat.adapters.RecentConversationsAdapter;
import com.bluej.chitchat.databinding.ActivityMainBinding;
import com.bluej.chitchat.listeners.ConversionListener;
import com.bluej.chitchat.models.ChatMessage;
import com.bluej.chitchat.models.User;
import com.bluej.chitchat.utilities.Constants;
import com.bluej.chitchat.utilities.PreferenceManager;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingService;

import java.util.*;

public class MainActivity extends BaseActivity implements ConversionListener {
    private ActivityMainBinding binding;
    private PreferenceManager preferenceManager;
    private List<ChatMessage> conversations;
    private RecentConversationsAdapter conversationsAdapter;
    private FirebaseFirestore database;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding=ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        preferenceManager=new PreferenceManager(getApplicationContext());
        init();
        loadUserDetails();
        setListeners();
        getToken();
        listenConversations();

    }
    private void init(){
        conversations=new ArrayList<>();
        conversationsAdapter=new RecentConversationsAdapter(conversations,this);
        binding.conversationsRecyclerView.setAdapter(conversationsAdapter);
        database=FirebaseFirestore.getInstance();
    }
    private void setListeners(){
        binding.imagesignOut.setOnClickListener(view -> signOut());
        binding.fabNewChat.setOnClickListener(view ->
                startActivity(new Intent(getApplicationContext(),UserActivity.class)));
    }

    private void loadUserDetails(){
        binding.textName.setText(preferenceManager.getString(Constants.KEY_NAME));
        try {
            byte[] bytes = Base64.decode(preferenceManager.getString(Constants.KEY_IMAGE), Base64.DEFAULT);
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            binding.imageprofile.setImageBitmap(bitmap);
        } catch (IllegalArgumentException e) {
            Log.e("main", "Exception on loading profile image: " + e.getMessage());
        }

    }
    private void showToast(String message){
        Toast.makeText(this,message, Toast.LENGTH_SHORT).show();
    }
    private void listenConversations(){
        database.collection(Constants.KEY_COLLECTION_CONVERSATIONS)
                .whereEqualTo(Constants.KEY_SENDER_ID,preferenceManager.getString(Constants.KEY_USER_ID))
                .addSnapshotListener(eventListener);
        database.collection(Constants.KEY_COLLECTION_CONVERSATIONS)
                .whereEqualTo(Constants.KEY_RECEIVER_ID,preferenceManager.getString(Constants.KEY_USER_ID))
                .addSnapshotListener(eventListener);
    }
    private final EventListener<QuerySnapshot> eventListener =(value, error) -> {
        if(error!=null){
            return;
        }
        if(value!=null){
            for(DocumentChange documentChange:value.getDocumentChanges()){
                if((documentChange.getType() == DocumentChange.Type.ADDED)){
                String senderId=documentChange.getDocument().getString(Constants.KEY_SENDER_ID);
                String receiverId=documentChange.getDocument().getString(Constants.KEY_RECEIVER_ID);
                ChatMessage chatMessage=new ChatMessage();
                chatMessage.senderId=senderId;
                chatMessage.receiverId=receiverId;
                if(preferenceManager.getString(Constants.KEY_USER_ID).equals(senderId)){
                    chatMessage.conversionImage=documentChange.getDocument().getString(Constants.KEY_RECEIVER_IMAGE);
                    chatMessage.conversionName=documentChange.getDocument().getString(Constants.KEY_RECEIVER_NAME);
                    chatMessage.conversionId=documentChange.getDocument().getString(Constants.KEY_RECEIVER_ID);
                }else{
                    chatMessage.conversionImage=documentChange.getDocument().getString(Constants.KEY_SENDER_IMAGE);
                    chatMessage.conversionName=documentChange.getDocument().getString(Constants.KEY_SENDER_NAME);
                    chatMessage.conversionId=documentChange.getDocument().getString(Constants.KEY_SENDER_ID);
                }
                chatMessage.message=documentChange.getDocument().getString(Constants.KEY_LAST_MESSAGE);
                chatMessage.dateObject=documentChange.getDocument().getDate(Constants.KEY_TIMESTAMP);
                conversations.add(chatMessage);
            }else if(documentChange.getType() ==DocumentChange.Type.MODIFIED){
                    for(int i=0;i<conversations.size();i++){
                        String senderId=documentChange.getDocument().getString(Constants.KEY_SENDER_ID);
                        String receiverId=documentChange.getDocument().getString(Constants.KEY_RECEIVER_ID);
                        if(conversations.get(i).senderId.equals(senderId) && conversations.get(i).receiverId.equals(receiverId)){
                            conversations.get(i).message=documentChange.getDocument().getString(Constants.KEY_LAST_MESSAGE);
                            conversations.get(i).dateObject=documentChange.getDocument().getDate(Constants.KEY_TIMESTAMP);
                            break;
                        }
                    }
                }
        }
            Collections.sort(conversations,(obj1,obj2) -> obj2.dateObject.compareTo(obj1.dateObject));
            conversationsAdapter.notifyDataSetChanged();
            binding.conversationsRecyclerView.smoothScrollToPosition(0);
            binding.conversationsRecyclerView.setVisibility(View.VISIBLE);
            binding.progressBar.setVisibility(View.GONE);
        }
    };
    private void getToken(){
        FirebaseMessaging.getInstance().getToken().addOnSuccessListener(this::updateToken);
    }
    private void updateToken(String token){
        preferenceManager.putString(Constants.KEY_FCM_TOKEN,token);
        FirebaseFirestore fdb=FirebaseFirestore.getInstance();
        DocumentReference documentReference=
                fdb.collection(Constants.KEY_COLLECTION_USERS).document(
                        preferenceManager.getString(Constants.KEY_USER_ID)
                );
        documentReference.update(Constants.KEY_FCM_TOKEN,token)
                .addOnFailureListener(e -> showToast("Unable to update token"));
    }
    private void signOut(){
        showToast("Signing Out");
        FirebaseFirestore fdb=FirebaseFirestore.getInstance();
        DocumentReference documentReference=
                fdb.collection(Constants.KEY_COLLECTION_USERS).document(
                        preferenceManager.getString(Constants.KEY_USER_ID)
                );
        HashMap<String,Object> updates=new HashMap<>();
        updates.put(Constants.KEY_FCM_TOKEN, FieldValue.delete());
        documentReference.update(updates)
                .addOnSuccessListener(unused -> {
                    preferenceManager.clear();
                    startActivity(new Intent(getApplicationContext(),SigninActivity.class));
                    finish();
                })
                .addOnFailureListener(e->showToast("Unable to sign out"));
    }

    @Override
    public void onConversionClicked(User user){
        Intent intent=new Intent(getApplicationContext(),ChatActivity.class);
        intent.putExtra(Constants.KEY_USER,user);
        startActivity(intent);
    }
}