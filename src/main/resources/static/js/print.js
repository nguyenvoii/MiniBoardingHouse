/*
 * MiniBoardingHouse - Print page
 * API_ENDPOINT cần đổi thành endpoint thật của backend sau khi xác nhận Controller.
 * Nếu API chưa được cấu hình/không tồn tại, trang dùng dữ liệu minh họa.
 */
const API_ENDPOINT = ""; // Ví dụ: "/api/toan/reports/print-data"

const sample = {
  overview: [
    { code: "P.101", type: "Phòng đơn", price: 2500000, status: "Đang thuê" },
    { code: "P.102", type: "Phòng đôi", price: 3500000, status: "Còn trống" },
    { code: "P.103", type: "Phòng đơn", price: 2500000, status: "Đang thuê" },
    { code: "P.104", type: "Phòng đơn", price: 2700000, status: "Đang thuê" },
    { code: "P.105", type: "Phòng đôi", price: 3500000, status: "Còn trống" },
    { code: "P.106", type: "Phòng đơn", price: 2500000, status: "Đang thuê" }
  ],
  room: [
    { code: "P.101", type: "Phòng đơn", price: 2500000, status: "Đang thuê" },
    { code: "P.102", type: "Phòng đôi", price: 3500000, status: "Còn trống" },
    { code: "P.103", type: "Phòng đơn", price: 2500000, status: "Đang thuê" }
  ],
  tenant: [
    { code: "NT001", type: "Nguyễn Văn A", price: "P.101", status: "Đang thuê" },
    { code: "NT002", type: "Trần Thị B", price: "P.103", status: "Đang thuê" }
  ]
};

let currentData = sample.overview;
const money = value => typeof value === "number"
  ? new Intl.NumberFormat("vi-VN").format(value) + " ₫" : (value ?? "—");

function renderRows(rows) {
  const body = document.getElementById("tableBody");
  body.innerHTML = "";
  rows.forEach((item, index) => {
    const statusClass = String(item.status || "").toLowerCase().includes("trống") ? "vacant" : "occupied";
    const tr = document.createElement("tr");
    [index + 1, item.code ?? item.roomCode ?? item.id ?? "—",
      item.type ?? item.name ?? "—", money(item.price ?? item.rentPrice),
      item.status ?? "—"].forEach((value, i) => {
        const td = document.createElement("td");
        if (i === 4) {
          const pill = document.createElement("span");
          pill.className = "pill " + statusClass;
          pill.textContent = value;
          td.appendChild(pill);
        } else td.textContent = value;
        tr.appendChild(td);
      });
    body.appendChild(tr);
  });
  document.getElementById("recordCount").textContent = rows.length + " bản ghi";
}

function applyFilter() {
  const keyword = document.getElementById("searchInput").value.trim().toLowerCase();
  const filtered = currentData.filter(row => JSON.stringify(row).toLowerCase().includes(keyword));
  renderRows(filtered);
}

function setType(type) {
  const titles = {
    overview: ["BÁO CÁO TỔNG QUAN", "Tổng hợp thông tin quản lý nhà trọ", "DANH SÁCH PHÒNG"],
    room: ["DANH SÁCH PHÒNG", "Thông tin phòng và tình trạng sử dụng", "CHI TIẾT DANH SÁCH PHÒNG"],
    tenant: ["DANH SÁCH NGƯỜI THUÊ", "Thông tin người thuê đang được quản lý", "DANH SÁCH NGƯỜI THUÊ"]
  };
  const data = Array.isArray(window.__printApiData) ? window.__printApiData : sample[type];
  currentData = data || [];
  document.getElementById("reportTitle").textContent = titles[type][0];
  document.getElementById("reportSubtitle").textContent = titles[type][1];
  document.getElementById("tableTitle").textContent = titles[type][2];
  document.getElementById("summaryGrid").style.display = type === "overview" ? "grid" : "none";
  document.getElementById("documentCode").textContent = "MBH-" + type.toUpperCase() + "-" + new Date().toISOString().slice(0,10).replaceAll("-","");
  applyFilter();
}

async function loadApiData() {
  const status = document.getElementById("loadStatus");
  if (!API_ENDPOINT) {
    status.textContent = "Dữ liệu minh họa – chưa nối API";
    return;
  }
  try {
    const response = await fetch(API_ENDPOINT, { headers: { "Accept": "application/json" } });
    if (!response.ok) throw new Error("HTTP " + response.status);
    const payload = await response.json();
    const rows = Array.isArray(payload) ? payload : (payload.data || payload.content || []);
    if (!Array.isArray(rows)) throw new Error("API không trả về danh sách");
    window.__printApiData = rows;
    setType(document.getElementById("documentType").value);
    status.textContent = "Đã tải dữ liệu từ hệ thống";
    status.style.background = "#e8f7ef";
    status.style.color = "#16804a";
  } catch (error) {
    status.textContent = "Không tải được API – đang dùng dữ liệu mẫu";
    console.error("Print API error:", error);
  }
}

document.getElementById("documentType").addEventListener("change", e => setType(e.target.value));
document.getElementById("searchInput").addEventListener("input", applyFilter);
document.getElementById("btnPrint").addEventListener("click", () => window.print());
document.getElementById("btnReload").addEventListener("click", loadApiData);
document.getElementById("createdDate").textContent = new Intl.DateTimeFormat("vi-VN", { dateStyle: "long" }).format(new Date());
setType("overview");
loadApiData();
