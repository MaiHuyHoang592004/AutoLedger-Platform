// === create-listing-map.js - VERSION FIX 100% ===
let map, marker;

document.addEventListener('DOMContentLoaded', function() {
    if (typeof L === 'undefined' || typeof GeoSearch === 'undefined') {
        setTimeout(arguments.callee, 100);
        return;
    }
    initMap();
});

function initMap() {
    console.log('🗺️ Init map...');
    const defaultLat = 21.0285;
    const defaultLng = 105.8542;
    
    map = L.map('map').setView([defaultLat, defaultLng], 12);
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png').addTo(map);

    marker = L.marker([defaultLat, defaultLng], { draggable: true }).addTo(map);
    updateCoordinates(defaultLat, defaultLng);  // lat FIRST

    // ✅ FIX: Drag marker
    marker.on('dragend', e => {
        const latlng = e.target.getLatLng();
        updateCoordinates(latlng.lat, latlng.lng);
    });

    // ✅ FIX: Click map
    map.on('click', e => {
        marker.setLatLng(e.latlng);
        updateCoordinates(e.latlng.lat, e.latlng.lng);
    });

    // ✅ GeoSearch
    const provider = new GeoSearch.OpenStreetMapProvider();
    const search = new GeoSearch.GeoSearchControl({
        provider: provider,
        style: 'bar',
        searchLabel: '🔍 Tìm địa chỉ...',
        autoComplete: true,
        showMarker: false  // Không tạo marker mới
    });
    map.addControl(search);

    // 🔥 FIX CHÍNH: GeoSearch Event - KHÔNG SWAP lat/lng!
    map.on('geosearch/showlocation', e => {
        console.log('🔍 Search result:', e.location);
        const lat = e.location.y;  // LATITUDE
        const lng = e.location.x;  // LONGITUDE
        marker.setLatLng([lat, lng]);
        updateCoordinates(lat, lng);
    });
}

async function updateCoordinates(lat, lng) {
    console.log(`📍 Update: lat=${lat}, lng=${lng}`);
    
    document.getElementById('latitude').value = lat.toFixed(6);
    document.getElementById('longitude').value = lng.toFixed(6);
    
    // City async - không block
    const city = await getCity(lat, lng);
    document.getElementById('homeCity').value = city;
    console.log(`🏙️ City: ${city}`);
}

async function getCity(lat, lng) {
    try {
        const res = await fetch(
            `https://nominatim.openstreetmap.org/reverse?format=json&lat=${lat}&lon=${lng}&zoom=10&addressdetails=1`,
            { headers: { 'User-Agent': 'CarRental/1.0' } }
        );
        const data = await res.json();
        return data.address?.city || data.address?.town || data.address?.village || 
               data.address?.city_district || 'Hà Nội';
    } catch (e) {
        console.error('❌ City error:', e);
        return 'Hà Nội';  // Default
    }
}

// 🔥 NGĂN SUBMIT NẾU CHƯA CHỌN VỊ TRÍ
document.querySelector('form')?.addEventListener('submit', function(e) {
    const lat = parseFloat(document.getElementById('latitude').value);
    const lng = parseFloat(document.getElementById('longitude').value);
    const city = document.getElementById('homeCity').value.trim();
    
    console.log('Submit check:', {lat, lng, city});
    
    if (isNaN(lat) || isNaN(lng) || !city || city === 'Không xác định') {
        e.preventDefault();
        alert('❌ VUI LÒNG **CHỌN VỊ TRÍ** TRÊN BẢN ĐỒ trước khi tạo bài đăng!');
        return false;
    }
});