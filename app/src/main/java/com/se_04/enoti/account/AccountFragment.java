package com.se_04.enoti.account;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.HttpHeaderParser;
import com.android.volley.toolbox.Volley;
import com.se_04.enoti.R;
import com.se_04.enoti.account_related.LogInActivity;
import com.se_04.enoti.utils.UserManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class AccountFragment extends Fragment {

    private ImageView imgAvatar;
    private TextView txtFullName, txtApartment, email, phoneNumber, relationship, startDate;
    private Button btnChangePassword, btnSignOut;

    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private static final int REQUEST_IMAGE_PICK = 2;
    private String currentPhotoPath;

    // 🔥 URL API cho avatar
    private static final String UPLOAD_AVATAR_URL = "http://10.0.2.2:5000/api/avatar/upload";
    private static final String GET_AVATAR_URL = "http://10.0.2.2:5000/api/avatar/user/";

    // 🔥 Thêm biến để kiểm tra fragment state
    private boolean isFragmentDestroyed = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.activity_account, container, false);

        // 🔥 Reset state khi fragment được tạo lại
        isFragmentDestroyed = false;

        // Ánh xạ view
        imgAvatar = view.findViewById(R.id.imgAvatar);
        txtFullName = view.findViewById(R.id.txtFullName);
        txtApartment = view.findViewById(R.id.txtApartment);
        email = view.findViewById(R.id.email);
        phoneNumber = view.findViewById(R.id.phoneNumber);
        relationship = view.findViewById(R.id.relationship);
        startDate = view.findViewById(R.id.startDate);
        btnChangePassword = view.findViewById(R.id.btnChangePassword);
        btnSignOut = view.findViewById(R.id.btnSignOut);

        // 🔹 Lấy thông tin user từ UserManager
        UserManager userManager = UserManager.getInstance(requireContext());
        UserItem currentUser = userManager.getCurrentUser();

        // Nếu chưa có dữ liệu thì tạo demo user
        if (currentUser == null) {
            currentUser = new UserItem(
                    "U01",
                    "F01",
                    "a.nguyenvan@example.com",
                    "Nguyễn Văn A",
                    "12/03/1950",
                    Gender.MALE,
                    "Chủ hộ",
                    0,
                    Role.USER,
                    "0987654321"
            );
            userManager.saveCurrentUser(currentUser);
        }

        // Gán dữ liệu lên giao diện
        bindUserData(currentUser);

        // 🔹 Click ảnh đại diện để thay đổi - THÊM HIỆU ỨNG CLICK
        imgAvatar.setOnClickListener(v -> {
            Log.d("AvatarDebug", "ImageView clicked - showing dialog");

            // 🔥 Kiểm tra fragment còn active không
            if (isFragmentDestroyed || !isAdded() || getContext() == null) {
                Log.d("AvatarDebug", "Fragment not attached, ignoring click");
                return;
            }

            // Thêm hiệu ứng click
            v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100)
                    .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(100).start())
                    .start();
            showImagePickerDialog();
        });

        // 🔹 Nút chỉnh sửa hồ sơ
        btnChangePassword.setOnClickListener(v -> {
            if (isFragmentDestroyed) return;
            Intent editIntent = new Intent(requireContext(), ChangePasswordActivity.class);
            startActivity(editIntent);
        });

        // 🔹 Nút đăng xuất
        btnSignOut.setOnClickListener(v -> {
            if (isFragmentDestroyed) return;
            showLogoutConfirmation();
        });

        return view;
    }

    // 🔥 PHƯƠNG THỨC KIỂM TRA FRAGMENT STATE
    private boolean isFragmentAttached() {
        return !isFragmentDestroyed && isAdded() && getContext() != null && getActivity() != null;
    }

    private void bindUserData(UserItem user) {
        if (!isFragmentAttached()) return;

        txtFullName.setText(user.getName());
        txtApartment.setText("Căn hộ: " + user.getRoom());
        email.setText("Email: " + user.getEmail());
        phoneNumber.setText("Số điện thoại: " + user.getPhone());
        relationship.setText("Quan hệ trong hộ: " + user.getRelationship());
        startDate.setText("Ngày sinh: " + user.getDob());

        // 🔹 TẠM THỜI CHỈ LOAD TỪ LOCAL ĐỂ TEST - COMMENT SERVER LOAD
        loadAvatarFromLocal(user.getId());

        // 🔹 Nếu muốn load từ server, comment dòng trên và bỏ comment dòng dưới:
        // loadAvatarFromServer(user.getId());

        // 🔹 Nếu là Admin → ẩn các trường không cần thiết
        if (user.getRole() == Role.ADMIN) {
            txtApartment.setText("Quản trị viên");
            relationship.setVisibility(View.GONE);
        } else {
            txtApartment.setVisibility(View.VISIBLE);
            relationship.setVisibility(View.VISIBLE);
        }
    }

    private void loadAvatarFromServer(String userId) {
        if (!isFragmentAttached()) {
            Log.d("AvatarDebug", "Fragment not attached, skipping server load");
            return;
        }
        checkServerAvatar(userId);
    }

    private void checkServerAvatar(String userId) {
        if (!isFragmentAttached()) {
            Log.d("AvatarDebug", "Fragment not attached, skipping server check");
            return;
        }

        String url = GET_AVATAR_URL + userId;

        RequestQueue queue = Volley.newRequestQueue(requireContext());

        com.android.volley.toolbox.StringRequest stringRequest = new com.android.volley.toolbox.StringRequest(
                Request.Method.GET, url,
                response -> {
                    // 🔥 KIỂM TRA FRAGMENT CÒN ATTACHED KHÔNG
                    if (!isFragmentAttached()) {
                        Log.d("AvatarDebug", "Fragment detached, ignoring server response");
                        return;
                    }

                    try {
                        JSONObject jsonResponse = new JSONObject(response);
                        if (jsonResponse.getBoolean("success")) {
                            JSONObject userObj = jsonResponse.getJSONObject("user");
                            if (userObj.getBoolean("hasAvatar")) {
                                String avatarUrl = userObj.getString("avatarUrl");
                                // Load ảnh từ server URL
                                loadImageFromUrl(avatarUrl);
                                return;
                            }
                        }
                        // Nếu server không có avatar, load từ local
                        loadAvatarFromLocal(userId);
                    } catch (JSONException e) {
                        e.printStackTrace();
                        loadAvatarFromLocal(userId);
                    }
                },
                error -> {
                    // 🔥 KIỂM TRA FRAGMENT CÒN ATTACHED KHÔNG
                    if (!isFragmentAttached()) {
                        Log.d("AvatarDebug", "Fragment detached, ignoring server error");
                        return;
                    }
                    // Nếu có lỗi, load từ local
                    loadAvatarFromLocal(userId);
                }
        );

        queue.add(stringRequest);
    }

    private void loadImageFromUrl(String imageUrl) {
        if (!isFragmentAttached()) {
            Log.d("AvatarDebug", "Fragment not attached, skipping image load from URL");
            return;
        }

        String fullUrl = "http://10.0.2.2:5000" + imageUrl;

        com.android.volley.toolbox.ImageRequest imageRequest = new com.android.volley.toolbox.ImageRequest(
                fullUrl,
                response -> {
                    // 🔥 KIỂM TRA FRAGMENT CÒN ATTACHED KHÔNG
                    if (!isFragmentAttached()) {
                        Log.d("AvatarDebug", "Fragment detached, ignoring image response");
                        return;
                    }

                    imgAvatar.setImageBitmap(response);
                    // Lưu ảnh vào local storage để cache
                    saveBitmapToLocal(response, UserManager.getInstance(requireContext()).getCurrentUser().getId());
                },
                0, 0, ImageView.ScaleType.CENTER_CROP, Bitmap.Config.RGB_565,
                error -> {
                    // 🔥 KIỂM TRA FRAGMENT CÒN ATTACHED KHÔNG
                    if (!isFragmentAttached()) {
                        Log.d("AvatarDebug", "Fragment detached, ignoring image error");
                        return;
                    }
                    // Nếu load từ URL thất bại, load từ local
                    loadAvatarFromLocal(UserManager.getInstance(requireContext()).getCurrentUser().getId());
                }
        );

        RequestQueue queue = Volley.newRequestQueue(requireContext());
        queue.add(imageRequest);
    }

    private void loadAvatarFromLocal(String userId) {
        // 🔥 KIỂM TRA FRAGMENT CÒN ATTACHED KHÔNG
        if (!isFragmentAttached()) {
            Log.d("AvatarDebug", "Fragment not attached, skipping local load");
            return;
        }

        try {
            File avatarFile = getAvatarFile(userId);
            if (avatarFile.exists()) {
                Bitmap bitmap = BitmapFactory.decodeFile(avatarFile.getAbsolutePath());
                imgAvatar.setImageBitmap(bitmap);
                Log.d("AvatarDebug", "Loaded avatar from local storage");
            } else {
                // Set ảnh mặc định theo giới tính
                setDefaultAvatar();
            }
        } catch (Exception e) {
            e.printStackTrace();
            setDefaultAvatar();
        }
    }

    private void setDefaultAvatar() {
        // 🔥 KIỂM TRA FRAGMENT CÒN ATTACHED KHÔNG
        if (!isFragmentAttached()) {
            Log.d("AvatarDebug", "Fragment not attached, skipping default avatar");
            return;
        }

        UserItem currentUser = UserManager.getInstance(requireContext()).getCurrentUser();
        if (currentUser != null) {
            imgAvatar.setImageResource(
                    currentUser.getGender() == Gender.MALE
                            ? R.drawable.ic_person
                            : R.drawable.ic_person_female
            );
            Log.d("AvatarDebug", "Set default avatar based on gender");
        }
    }

    private void showImagePickerDialog() {
        if (!isFragmentAttached()) {
            Log.d("AvatarDebug", "Fragment not attached, cannot show dialog");
            return;
        }

        String[] options = {"Chụp ảnh", "Chọn từ thư viện", "Hủy"};

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Chọn ảnh đại diện");
        builder.setItems(options, (dialog, which) -> {
            if (!isFragmentAttached()) return;

            if (which == 0) {
                Log.d("AvatarDebug", "User selected camera");
                openCamera();
            } else if (which == 1) {
                Log.d("AvatarDebug", "User selected gallery");
                openGallery();
            }
        });

        // Thêm cancel listener
        builder.setOnCancelListener(dialog -> {
            Log.d("AvatarDebug", "Image picker dialog cancelled");
        });

        try {
            builder.show();
            Log.d("AvatarDebug", "Image picker dialog shown");
        } catch (Exception e) {
            Log.e("AvatarDebug", "Error showing dialog: " + e.getMessage());
        }
    }

    private void openCamera() {
        if (!isFragmentAttached()) return;

        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(requireActivity().getPackageManager()) != null) {
            File photoFile = createImageFile();
            if (photoFile != null) {
                Uri photoURI = FileProvider.getUriForFile(requireContext(),
                        requireContext().getPackageName() + ".provider",
                        photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
                Log.d("AvatarDebug", "Camera intent started");
            }
        } else {
            Toast.makeText(requireContext(), "Không tìm thấy ứng dụng máy ảnh", Toast.LENGTH_SHORT).show();
        }
    }

    private void openGallery() {
        if (!isFragmentAttached()) return;

        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/jpeg", "image/png"});
        startActivityForResult(intent, REQUEST_IMAGE_PICK);
        Log.d("AvatarDebug", "Gallery intent started");
    }

    private File createImageFile() {
        if (!isFragmentAttached()) return null;

        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES);

        try {
            File image = File.createTempFile(imageFileName, ".jpg", storageDir);
            currentPhotoPath = image.getAbsolutePath();
            Log.d("AvatarDebug", "Created image file: " + currentPhotoPath);
            return image;
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(requireContext(), "Lỗi khi tạo file ảnh", Toast.LENGTH_SHORT).show();
            return null;
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // 🔥 KIỂM TRA FRAGMENT CÒN ATTACHED KHÔNG
        if (!isFragmentAttached()) {
            Log.d("AvatarDebug", "Fragment not attached, ignoring activity result");
            return;
        }

        if (resultCode == getActivity().RESULT_OK) {
            if (requestCode == REQUEST_IMAGE_CAPTURE) {
                // Xử lý ảnh từ camera
                Log.d("AvatarDebug", "Camera result OK, processing image");
                processImage(currentPhotoPath);
            } else if (requestCode == REQUEST_IMAGE_PICK && data != null) {
                // Xử lý ảnh từ gallery
                Log.d("AvatarDebug", "Gallery result OK, processing image");
                Uri selectedImage = data.getData();
                String imagePath = getRealPathFromURI(selectedImage);
                if (imagePath != null) {
                    processImage(imagePath);
                }
            }
        } else {
            Log.d("AvatarDebug", "Activity result cancelled");
        }
    }

    private String getRealPathFromURI(Uri contentUri) {
        if (!isFragmentAttached()) return null;

        String[] proj = {MediaStore.Images.Media.DATA};
        Cursor cursor = requireContext().getContentResolver().query(contentUri, proj, null, null, null);
        if (cursor != null) {
            int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            cursor.moveToFirst();
            String path = cursor.getString(column_index);
            cursor.close();
            return path;
        }
        return contentUri.getPath();
    }

    private void processImage(String imagePath) {
        if (!isFragmentAttached()) {
            Log.d("AvatarDebug", "Fragment not attached, cannot process image");
            return;
        }

        try {
            Log.d("AvatarDebug", "Processing image: " + imagePath);

            // Nén và resize ảnh
            File compressedFile = compressImage(imagePath);

            // Lưu ảnh với tên là userId
            UserItem currentUser = UserManager.getInstance(requireContext()).getCurrentUser();
            File finalAvatarFile = saveAvatarForUser(compressedFile, currentUser.getId());

            // Hiển thị ảnh lên ImageView
            Bitmap bitmap = BitmapFactory.decodeFile(finalAvatarFile.getAbsolutePath());
            imgAvatar.setImageBitmap(bitmap);

            // 🔥 UPLOAD ẢNH LÊN SERVER
            uploadAvatarToServer(finalAvatarFile, currentUser.getId());

            Toast.makeText(requireContext(), "Đã cập nhật ảnh đại diện", Toast.LENGTH_SHORT).show();

            // Xóa file tạm
            if (compressedFile.exists()) {
                compressedFile.delete();
            }

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(requireContext(), "Lỗi khi xử lý ảnh", Toast.LENGTH_SHORT).show();
        }
    }

    // 🔥 PHƯƠNG THỨC UPLOAD AVATAR LÊN SERVER
    private void uploadAvatarToServer(File imageFile, String userId) {
        if (!isFragmentAttached()) {
            Log.d("AvatarDebug", "Fragment not attached, skipping upload");
            return;
        }

        try {
            VolleyMultipartRequest multipartRequest = new VolleyMultipartRequest(
                    Request.Method.POST, UPLOAD_AVATAR_URL,
                    new Response.Listener<NetworkResponse>() {
                        @Override
                        public void onResponse(NetworkResponse response) {
                            // 🔥 KIỂM TRA FRAGMENT CÒN ATTACHED KHÔNG
                            if (!isFragmentAttached()) {
                                Log.d("AvatarDebug", "Fragment detached, ignoring upload response");
                                return;
                            }

                            try {
                                String jsonString = new String(response.data, HttpHeaderParser.parseCharset(response.headers));
                                JSONObject result = new JSONObject(jsonString);

                                if (result.getBoolean("success")) {
                                    Toast.makeText(requireContext(), "Đã cập nhật avatar lên server", Toast.LENGTH_SHORT).show();
                                    Log.d("AvatarDebug", "Avatar uploaded successfully to server");
                                } else {
                                    Toast.makeText(requireContext(), "Lỗi: " + result.getString("error"), Toast.LENGTH_SHORT).show();
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                                Toast.makeText(requireContext(), "Lỗi khi xử lý phản hồi", Toast.LENGTH_SHORT).show();
                            }
                        }
                    },
                    new Response.ErrorListener() {
                        @Override
                        public void onErrorResponse(VolleyError error) {
                            // 🔥 KIỂM TRA FRAGMENT CÒN ATTACHED KHÔNG
                            if (!isFragmentAttached()) {
                                Log.d("AvatarDebug", "Fragment detached, ignoring upload error");
                                return;
                            }

                            error.printStackTrace();
                            Toast.makeText(requireContext(), "Lỗi kết nối đến server", Toast.LENGTH_SHORT).show();
                        }
                    }) {

                @Override
                protected Map<String, String> getParams() {
                    Map<String, String> params = new HashMap<>();
                    params.put("userId", userId);
                    return params;
                }

                @Override
                protected Map<String, DataPart> getByteData() {
                    Map<String, DataPart> params = new HashMap<>();
                    try {
                        params.put("avatar", new DataPart("avatar.jpg", getFileDataFromFile(imageFile), "image/jpeg"));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    return params;
                }
            };

            RequestQueue queue = Volley.newRequestQueue(requireContext());
            queue.add(multipartRequest);
            Log.d("AvatarDebug", "Upload request sent to server");

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(requireContext(), "Lỗi khi upload avatar", Toast.LENGTH_SHORT).show();
        }
    }

    private byte[] getFileDataFromFile(File file) throws IOException {
        FileInputStream fileInputStream = new FileInputStream(file);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] b = new byte[1024];
        int bytesRead;
        while ((bytesRead = fileInputStream.read(b)) != -1) {
            bos.write(b, 0, bytesRead);
        }
        fileInputStream.close();
        return bos.toByteArray();
    }

    private File compressImage(String imagePath) throws IOException {
        Bitmap bitmap = BitmapFactory.decodeFile(imagePath);
        if (bitmap == null) {
            throw new IOException("Không thể đọc file ảnh");
        }

        // Resize ảnh để giảm kích thước
        int maxWidth = 800;
        int maxHeight = 800;
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        if (width > maxWidth || height > maxHeight) {
            float ratio = Math.min((float) maxWidth / width, (float) maxHeight / height);
            int newWidth = (int) (width * ratio);
            int newHeight = (int) (height * ratio);

            Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
            bitmap.recycle();
            bitmap = resizedBitmap;
        }

        // Nén ảnh
        File compressedFile = new File(requireContext().getFilesDir(), "temp_compressed.jpg");
        FileOutputStream fos = new FileOutputStream(compressedFile);
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, fos);
        fos.flush();
        fos.close();
        bitmap.recycle();

        return compressedFile;
    }

    private File saveAvatarForUser(File sourceFile, String userId) throws IOException {
        File avatarDir = new File(requireContext().getFilesDir(), "avatars");
        if (!avatarDir.exists()) {
            avatarDir.mkdirs();
        }

        File destinationFile = new File(avatarDir, userId + ".jpg");

        // Copy file
        Bitmap bitmap = BitmapFactory.decodeFile(sourceFile.getAbsolutePath());
        FileOutputStream fos = new FileOutputStream(destinationFile);
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
        fos.flush();
        fos.close();
        bitmap.recycle();

        return destinationFile;
    }

    private void saveBitmapToLocal(Bitmap bitmap, String userId) {
        try {
            File avatarDir = new File(requireContext().getFilesDir(), "avatars");
            if (!avatarDir.exists()) {
                avatarDir.mkdirs();
            }

            File destinationFile = new File(avatarDir, userId + ".jpg");
            FileOutputStream fos = new FileOutputStream(destinationFile);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
            fos.flush();
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private File getAvatarFile(String userId) {
        File avatarDir = new File(requireContext().getFilesDir(), "avatars");
        return new File(avatarDir, userId + ".jpg");
    }

    private void showLogoutConfirmation() {
        if (!isFragmentAttached()) return;

        new AlertDialog.Builder(requireContext())
                .setTitle("Xác nhận đăng xuất")
                .setMessage("Bạn có chắc chắn muốn đăng xuất không?")
                .setPositiveButton("Đăng xuất", (dialog, which) -> logout())
                .setNegativeButton("Hủy", (dialog, which) -> dialog.dismiss())
                .setCancelable(false)
                .show();
    }

    private void logout() {
        if (!isFragmentAttached()) return;

        // Xóa toàn bộ dữ liệu người dùng khỏi UserManager
        UserManager.getInstance(requireContext()).clearUser();

        // Xóa mọi dữ liệu khác nếu có SharedPreferences khác
        requireContext().getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply();

        // Quay lại màn hình đăng nhập, xóa ngăn xếp Activity
        Intent intent = new Intent(requireContext(), LogInActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        requireActivity().finish();
    }

    // 🔥 THÊM FRAGMENT LIFECYCLE METHODS
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        isFragmentDestroyed = true;
        Log.d("AvatarDebug", "onDestroyView - Fragment view destroyed");
    }

    @Override
    public void onDetach() {
        super.onDetach();
        isFragmentDestroyed = true;
        Log.d("AvatarDebug", "onDetach - Fragment detached from activity");
    }

    // 🔥 LỚP VOLLEY MULTIPART REQUEST
    public class VolleyMultipartRequest extends Request<NetworkResponse> {
        private final Response.Listener<NetworkResponse> mListener;
        private final Response.ErrorListener mErrorListener;
        private final Map<String, String> mHeaders;
        private final String mMimeType;
        private final byte[] mMultipartBody;

        public VolleyMultipartRequest(int method, String url,
                                      Response.Listener<NetworkResponse> listener,
                                      Response.ErrorListener errorListener) {
            super(method, url, errorListener);
            this.mListener = listener;
            this.mErrorListener = errorListener;
            this.mMultipartBody = null;
            this.mMimeType = null;
            this.mHeaders = new HashMap<>();
        }

        @Override
        public Map<String, String> getHeaders() throws AuthFailureError {
            return (mHeaders != null) ? mHeaders : super.getHeaders();
        }

        @Override
        public String getBodyContentType() {
            return mMimeType;
        }

        @Override
        public byte[] getBody() throws AuthFailureError {
            return mMultipartBody;
        }

        @Override
        protected Response<NetworkResponse> parseNetworkResponse(NetworkResponse response) {
            return Response.success(response, HttpHeaderParser.parseCacheHeaders(response));
        }

        @Override
        protected void deliverResponse(NetworkResponse response) {
            mListener.onResponse(response);
        }

        protected Map<String, String> getParams() {
            return new HashMap<>();
        }

        protected Map<String, DataPart> getByteData() {
            return new HashMap<>();
        }

        public class DataPart {
            private String fileName;
            private byte[] content;
            private String type;

            public DataPart(String fileName, byte[] content, String type) {
                this.fileName = fileName;
                this.content = content;
                this.type = type;
            }

            public String getFileName() { return fileName; }
            public byte[] getContent() { return content; }
            public String getType() { return type; }
        }
    }
}