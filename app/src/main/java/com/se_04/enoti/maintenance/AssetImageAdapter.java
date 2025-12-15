package com.se_04.enoti.maintenance;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.se_04.enoti.R;
import java.util.List;

public class AssetImageAdapter extends RecyclerView.Adapter<AssetImageAdapter.ViewHolder> {

    private final List<String> imageUrls;
    private final OnImageClickListener listener;

    public interface OnImageClickListener {
        void onImageClick(String url);
    }

    public AssetImageAdapter(List<String> imageUrls, OnImageClickListener listener) {
        this.imageUrls = imageUrls;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Bạn cần tạo file layout item_asset_image.xml (code ở dưới cùng)
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_asset_image, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String url = imageUrls.get(position);

        Glide.with(holder.itemView.getContext())
                .load(url)
                .placeholder(R.drawable.bg_white_rounded)
                .error(R.drawable.ic_warning_circle)
                .centerCrop()
                .into(holder.imageView);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onImageClick(url);
        });
    }

    @Override
    public int getItemCount() { return imageUrls.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;
        ViewHolder(View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.imgAssetThumbnail);
        }
    }
}