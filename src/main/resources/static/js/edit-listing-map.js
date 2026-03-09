let map, marker;

document.addEventListener('DOMContentLoaded', function() {
    if (typeof L === 'undefined') return;
    initMap();
});

function initMap() {
    const lat = parseFloat(document.getElementById('latitude').value) || 21.0285;
    const lng = parseFloat(document.getElementById('longitude').value) || 105.8542;

    map = L.map('map').setView([lat, lng], 14);
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png').addTo(map);

    marker = L.marker([lat, lng], { draggable: true }).addTo(map);
    updateCoordinates(lat, lng);

    marker.on('dragend', e => {
        const pos = e.target.getLatLng();
        updateCoordinates(pos.lat, pos.lng);
    });

    map.on('click', e => {
        marker.setLatLng(e.latlng);
        updateCoordinates(e.latlng.lat, e.latlng.lng);
    });

    const provider = new GeoSearch.OpenStreetMapProvider();
    const search = new GeoSearch.GeoSearchControl({
        provider: provider,
        style: 'bar',
        showMarker: false
    });
    map.addControl(search);

    map.on('geosearch/showlocation', e => {
        const lat = e.location.y;
        const lng = e.location.x;
        marker.setLatLng([lat, lng]);
        updateCoordinates(lat, lng);
    });
}

async function updateCoordinates(lat, lng) {
    document.getElementById('latitude').value = lat.toFixed(6);
    document.getElementById('longitude').value = lng.toFixed(6);
    const city = await getCity(lat, lng);
    document.getElementById('homeCity').value = city;
}

async function getCity(lat, lng) {
    try {
        const res = await fetch(`https://nominatim.openstreetmap.org/reverse?format=json&lat=${lat}&lon=${lng}&zoom=10&addressdetails=1`);
        const data = await res.json();
        return data.address?.city || data.address?.town || 'Hà Nội';
    } catch (e) {
        return 'Hà Nội';
    }
}