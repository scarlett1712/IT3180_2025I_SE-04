package com.se_04.enoti.account;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
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
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.se_04.enoti.R;
import com.se_04.enoti.account_related.LogInActivity;
import com.se_04.enoti.utils.ApiConfig;
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
    private Button btnChangeInformtion, btnChangePassword, btnSignOut;

    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private static final int REQUEST_IMAGE_PICK = 2;
    private String currentPhotoPath;

    // URL API
    private static final String UPLOAD_AVATAR_URL = ApiConfig.BASE_URL + "/api/avatar/upload";
    private static final String GET_AVATAR_URL = ApiConfig.BASE_URL + "/api/avatar/user/";
    // 🔥 URL cho bảo mật
    private static final String CHECK_LOGIN_REQUEST_URL = ApiConfig.BASE_URL + "/api/users/check_pending_login/";
    private static final String RESOLVE_LOGIN_REQUEST_URL = ApiConfig.BASE_URL + "/api/users/resolve_login";

    private boolean isFragmentDestroyed = false;

    // 🔥 Polling Variables
    private Handler pollingHandler;
    private Runnable pollingRunnable;
    private boolean isDialogShowing = false; // Tránh hiện nhiều dialog cùng lúc

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.activity_account, container, false);
        isFragmentDestroyed = false;

        // Ánh xạ view
        imgAvatar = view.findViewById(R.id.imgAvatar);
        txtFullName = view.findViewById(R.id.txtFullName);
        txtApartment = view.findViewById(R.id.txtApartment);
        email = view.findViewById(R.id.email);
        phoneNumber = view.findViewById(R.id.phoneNumber);
        relationship = view.findViewById(R.id.relationship);
        startDate = view.findViewById(R.id.startDate);
        btnChangeInformtion = view.findViewById(R.id.btnChangeInformation);
        btnChangePassword = view.findViewById(R.id.btnChangePassword);
        btnSignOut = view.findViewById(R.id.btnSignOut);

        // Lấy thông tin user
        UserManager userManager = UserManager.getInstance(requireContext());
        UserItem currentUser = userManager.getCurrentUser();

        if (currentUser == null) {
            currentUser = new UserItem(
                    "U01", "F01", "a.nguyenvan@example.com", "Nguyễn Văn A",
                    "12/03/1950", Gender.MALE, "Chủ hộ", 0, Role.USER, "0987654321"
            );
            userManager.saveCurrentUser(currentUser);
        }

        bindUserData(currentUser);

        // Sự kiện click
        imgAvatar.setOnClickListener(v -> {
            if (isFragmentAttached()) {
                v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100)
                        .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(100).start()).start();
                showImagePickerDialog();
            }
        });

        btnChangeInformtion.setOnClickListener(v -> {
            if (!isFragmentDestroyed) startActivity(new Intent(requireContext(), EditProfileActivity.class));
        });

        btnChangePassword.setOnClickListener(v -> {
            if (!isFragmentDestroyed) startActivity(new Intent(requireContext(), ChangePasswordActivity.class));
        });

        btnSignOut.setOnClickListener(v -> {
            if (!isFragmentDestroyed) showLogoutConfirmation();
        });

        return view;
    }

    // 🔥 BẮT ĐẦU POLLING KHI MÀN HÌNH HIỆN
    @Override
    public void onResume() {
        super.onResume();
        startPolling();
    }

    // 🔥 DỪNG POLLING KHI RỜI MÀN HÌNH
    @Override
    public void onPause() {
        super.onPause();
        stopPolling();
    }

    private void startPolling() {
        if (pollingHandler == null) pollingHandler = new Handler(Looper.getMainLooper());

        pollingRunnable = new Runnable() {
            @Override
            public void run() {
                checkLoginRequests();
                // Lặp lại sau 5 giây
                if (!isFragmentDestroyed) pollingHandler.postDelayed(this, 5000);
            }
        };
        pollingHandler.post(pollingRunnable);
    }

    private void stopPolling() {
        if (pollingHandler != null && pollingRunnable != null) {
            pollingHandler.removeCallbacks(pollingRunnable);
        }
    }

    // 🔥 HÀM GỌI API KIỂM TRA
    private void checkLoginRequests() {
        if (!isFragmentAttached() || isDialogShowing) return;

        UserItem user = UserManager.getInstance(requireContext()).getCurrentUser();
        if (user == null) return;

        String url = CHECK_LOGIN_REQUEST_URL + user.getId();

        JsonArrayRequest request = new JsonArrayRequest(Request.Method.GET, url, null,
                response -> {
                    if (response.length() > 0) {
                        try {
                            // Có yêu cầu mới!
                            JSONObject req = response.getJSONObject(0);
                            int reqId = req.getInt("id");
                            showLoginRequestDialog(reqId);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                },
                error -> { /* Log lỗi âm thầm, không làm phiền user */ }
        );

        Volley.newRequestQueue(requireContext()).add(request);
    }

    // 🔥 HIỆN DIALOG CẢNH BÁO BẢO MẬT
    private void showLoginRequestDialog(int requestId) {
        if (!isFragmentAttached()) return;

        isDialogShowing = true; // Chặn polling hiện thêm dialog

        new AlertDialog.Builder(requireContext())
                .setTitle("⚠️ Cảnh báo bảo mật")
                .setMessage("Có một thiết bị khác đang cố gắng đăng nhập vào tài khoản của bạn.\n\nBạn có muốn cho phép không?")
                .setPositiveButton("Cho phép", (dialog, which) -> {
                    resolveLoginRequest(requestId, "approved");
                    isDialogShowing = false;
                })
                .setNegativeButton("Từ chối", (dialog, which) -> {
                    resolveLoginRequest(requestId, "rejected");
                    isDialogShowing = false;
                })
                .setCancelable(false)
                .show();
    }

    // 🔥 GỬI QUYẾT ĐỊNH LÊN SERVER
    private void resolveLoginRequest(int requestId, String action) {
        JSONObject body = new JSONObject();
        try {
            body.put("request_id", requestId);
            body.put("action", action);
        } catch (JSONException e) { e.printStackTrace(); }

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, RESOLVE_LOGIN_REQUEST_URL, body,
                response -> {
                    if (action.equals("approved")) {
                        Toast.makeText(requireContext(), "Đã cho phép...", Toast.LENGTH_LONG).show();
                        UserManager.getInstance(requireContext()).logoutLocal();
                    } else {
                        Toast.makeText(requireContext(), "Đã chặn đăng nhập lạ.", Toast.LENGTH_SHORT).show();
                    }
                },
                error -> {
                    Toast.makeText(requireContext(), "Lỗi kết nối", Toast.LENGTH_SHORT).show();
                    isDialogShowing = false; // Reset cờ nếu lỗi
                }
        );
        Volley.newRequestQueue(requireContext()).add(request);
    }

    private boolean isFragmentAttached() {
        return !isFragmentDestroyed && isAdded() && getContext() != null && getActivity() != null;
    }

    private void bindUserData(UserItem user) {
        if (!isFragmentAttached()) return;

        txtFullName.setText(user.getName());
        txtApartment.setText("Căn hộ: " + user.getRoom());
        email.setText("Email: " + user.getEmail());
        phoneNumber.setText("Số điện thoại: " + user.getPhone());
        relationship.setText("Quan hệ với chủ hộ: " + user.getRelationship());
        startDate.setText("Ngày sinh: " + user.getDob());

        loadAvatarFromLocal(user.getId());

        if (user.getRole() == Role.ADMIN) {
            txtApartment.setText("Quản trị viên");
            relationship.setVisibility(View.GONE);
        } else {
            txtApartment.setVisibility(View.VISIBLE);
            relationship.setVisibility(View.VISIBLE);
        }
    }

    // ------------------------------------------------------------------------
    // 🔥 CÁC PHƯƠNG THỨC XỬ LÝ ẢNH (CAMERA, GALLERY, UPLOAD)
    // ------------------------------------------------------------------------

    private void loadAvatarFromServer(String userId) {
        if (!isFragmentAttached()) return;
        checkServerAvatar(userId);
    }

    private void checkServerAvatar(String userId) {
        if (!isFragmentAttached()) return;
        String url = GET_AVATAR_URL + userId;
        RequestQueue queue = Volley.newRequestQueue(requireContext());

        com.android.volley.toolbox.StringRequest stringRequest = new com.android.volley.toolbox.StringRequest(
                Request.Method.GET, url,
                response -> {
                    if (!isFragmentAttached()) return;
                    try {
                        JSONObject jsonResponse = new JSONObject(response);
                        if (jsonResponse.getBoolean("success")) {
                            JSONObject userObj = jsonResponse.getJSONObject("user");
                            if (userObj.getBoolean("hasAvatar")) {
                                String avatarUrl = userObj.getString("avatarUrl");
                                loadImageFromUrl(avatarUrl);
                                return;
                            }
                        }
                        loadAvatarFromLocal(userId);
                    } catch (JSONException e) {
                        e.printStackTrace();
                        loadAvatarFromLocal(userId);
                    }
                },
                error -> {
                    if (!isFragmentAttached()) return;
                    loadAvatarFromLocal(userId);
                }
        );
        queue.add(stringRequest);
    }

    private void loadImageFromUrl(String imageUrl) {
        if (!isFragmentAttached()) return;
        String fullUrl = ApiConfig.BASE_URL + imageUrl;

        com.android.volley.toolbox.ImageRequest imageRequest = new com.android.volley.toolbox.ImageRequest(
                fullUrl,
                response -> {
                    if (!isFragmentAttached()) return;
                    imgAvatar.setImageBitmap(response);
                    saveBitmapToLocal(response, UserManager.getInstance(requireContext()).getCurrentUser().getId());
                },
                0, 0, ImageView.ScaleType.CENTER_CROP, Bitmap.Config.RGB_565,
                error -> {
                    if (!isFragmentAttached()) return;
                    loadAvatarFromLocal(UserManager.getInstance(requireContext()).getCurrentUser().getId());
                }
        );
        RequestQueue queue = Volley.newRequestQueue(requireContext());
        queue.add(imageRequest);
    }

    private void loadAvatarFromLocal(String userId) {
        if (!isFragmentAttached()) return;
        try {
            File avatarFile = getAvatarFile(userId);
            if (avatarFile.exists()) {
                Bitmap bitmap = BitmapFactory.decodeFile(avatarFile.getAbsolutePath());
                imgAvatar.setImageBitmap(bitmap);
            } else {
                setDefaultAvatar();
            }
        } catch (Exception e) {
            e.printStackTrace();
            setDefaultAvatar();
        }
    }

    private void setDefaultAvatar() {
        if (!isFragmentAttached()) return;
        UserItem currentUser = UserManager.getInstance(requireContext()).getCurrentUser();
        if (currentUser != null) {
            imgAvatar.setImageResource(
                    currentUser.getGender() == Gender.MALE
                            ? R.drawable.ic_person
                            : R.drawable.ic_person_female
            );
        }
    }

    private void showImagePickerDialog() {
        if (!isFragmentAttached()) return;
        String[] options = {"Chụp ảnh", "Chọn từ thư viện", "Hủy"};
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Chọn ảnh đại diện");
        builder.setItems(options, (dialog, which) -> {
            if (!isFragmentAttached()) return;
            if (which == 0) openCamera();
            else if (which == 1) openGallery();
        });
        builder.show();
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
    }

    private File createImageFile() {
        if (!isFragmentAttached()) return null;
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        try {
            File image = File.createTempFile(imageFileName, ".jpg", storageDir);
            currentPhotoPath = image.getAbsolutePath();
            return image;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (!isFragmentAttached()) return;

        if (resultCode == getActivity().RESULT_OK) {
            if (requestCode == REQUEST_IMAGE_CAPTURE) {
                processImage(currentPhotoPath);
            } else if (requestCode == REQUEST_IMAGE_PICK && data != null) {
                Uri selectedImage = data.getData();
                String imagePath = getRealPathFromURI(selectedImage);
                if (imagePath != null) processImage(imagePath);
            }
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
        if (!isFragmentAttached()) return;
        try {
            File compressedFile = compressImage(imagePath);
            UserItem currentUser = UserManager.getInstance(requireContext()).getCurrentUser();
            File finalAvatarFile = saveAvatarForUser(compressedFile, currentUser.getId());
            Bitmap bitmap = BitmapFactory.decodeFile(finalAvatarFile.getAbsolutePath());
            imgAvatar.setImageBitmap(bitmap);
            uploadAvatarToServer(finalAvatarFile, currentUser.getId());
            Toast.makeText(requireContext(), "Đã cập nhật ảnh đại diện", Toast.LENGTH_SHORT).show();
            if (compressedFile.exists()) compressedFile.delete();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(requireContext(), "Lỗi khi xử lý ảnh", Toast.LENGTH_SHORT).show();
        }
    }

    private void uploadAvatarToServer(File imageFile, String userId) {
        if (!isFragmentAttached()) return;
        try {
            VolleyMultipartRequest multipartRequest = new VolleyMultipartRequest(
                    Request.Method.POST, UPLOAD_AVATAR_URL,
                    response -> {
                        if (!isFragmentAttached()) return;
                        try {
                            String jsonString = new String(response.data, HttpHeaderParser.parseCharset(response.headers));
                            JSONObject result = new JSONObject(jsonString);
                            if (result.getBoolean("success")) {
                                Toast.makeText(requireContext(), "Đã cập nhật avatar lên server", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(requireContext(), "Lỗi server: " + result.getString("error"), Toast.LENGTH_SHORT).show();
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    },
                    error -> {
                        if (!isFragmentAttached()) return;
                        error.printStackTrace();
                        Toast.makeText(requireContext(), "Lỗi kết nối khi upload ảnh", Toast.LENGTH_SHORT).show();
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
        } catch (Exception e) {
            e.printStackTrace();
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
        if (bitmap == null) throw new IOException("Không thể đọc file ảnh");
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
        if (!avatarDir.exists()) avatarDir.mkdirs();
        File destinationFile = new File(avatarDir, userId + ".jpg");
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
            if (!avatarDir.exists()) avatarDir.mkdirs();
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
                .setPositiveButton("Đăng xuất", (dialog, which) -> {
                    // 🔥 Hiển thị loading
                    ProgressDialog progressDialog = new ProgressDialog(requireContext());
                    progressDialog.setMessage("Đang đăng xuất...");
                    progressDialog.setCancelable(false);
                    progressDialog.show();

                    // 🔥 Gọi hàm logout có callback
                    UserManager.getInstance(requireContext()).logout(new UserManager.LogoutCallback() {
                        @Override
                        public void onLogoutComplete() {
                            // Khi xong (hoặc lỗi), tắt dialog.
                            // UserManager sẽ tự chuyển màn hình.
                            if (progressDialog.isShowing()) {
                                progressDialog.dismiss();
                            }
                        }
                    });
                })
                .setNegativeButton("Hủy", (dialog, which) -> dialog.dismiss())
                .setCancelable(false)
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        isFragmentDestroyed = true;
        stopPolling(); // 🔥 Dừng polling khi destroy view
    }

    @Override
    public void onDetach() {
        super.onDetach();
        isFragmentDestroyed = true;
    }

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