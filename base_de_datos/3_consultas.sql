-- 10 consultas sobre la base de datos de la libreria

-- 1. Lista de productos con su categoria y su proveedor
SELECT p.nombre AS producto, p.precio, p.stock,
       c.nombre AS categoria, pr.nombre AS proveedor
FROM productos p
JOIN categorias c   ON p.id_categoria = c.id_categoria
JOIN proveedores pr ON p.id_proveedor = pr.id_proveedor
ORDER BY c.nombre, p.nombre;

-- 2. Cantidad de productos y stock total por categoria
SELECT c.nombre AS categoria,
       COUNT(*) AS cantidad_productos,
       SUM(p.stock) AS stock_total
FROM productos p
JOIN categorias c ON p.id_categoria = c.id_categoria
GROUP BY c.nombre;

-- 3. Total de cada venta con el nombre del cliente
SELECT v.id_venta, v.fecha, cl.nombre AS cliente,
       SUM(d.cantidad * d.precio_unitario) AS total
FROM ventas v
JOIN clientes cl     ON v.id_cliente = cl.id_cliente
JOIN detalle_venta d ON v.id_venta = d.id_venta
GROUP BY v.id_venta, v.fecha, cl.nombre
ORDER BY v.fecha;

-- 4. Cuanto ha gastado cada cliente y cuantas compras hizo
SELECT cl.nombre AS cliente,
       COUNT(DISTINCT v.id_venta) AS numero_compras,
       SUM(d.cantidad * d.precio_unitario) AS total_gastado
FROM clientes cl
JOIN ventas v        ON cl.id_cliente = v.id_cliente
JOIN detalle_venta d ON v.id_venta = d.id_venta
GROUP BY cl.nombre
ORDER BY total_gastado DESC;

-- 5. Los 5 productos mas vendidos (por unidades)
SELECT p.nombre AS producto,
       SUM(d.cantidad) AS unidades_vendidas
FROM detalle_venta d
JOIN productos p ON d.id_producto = p.id_producto
GROUP BY p.nombre
ORDER BY unidades_vendidas DESC
LIMIT 5;

-- 6. Ingresos por categoria
SELECT c.nombre AS categoria,
       SUM(d.cantidad * d.precio_unitario) AS ingresos
FROM detalle_venta d
JOIN productos p  ON d.id_producto = p.id_producto
JOIN categorias c ON p.id_categoria = c.id_categoria
GROUP BY c.nombre
ORDER BY ingresos DESC;

-- 7. Ventas por mes (numero de ventas e ingresos)
-- strftime es de SQLite, en MySQL seria DATE_FORMAT(v.fecha, '%Y-%m')
SELECT strftime('%Y-%m', v.fecha) AS mes,
       COUNT(DISTINCT v.id_venta) AS numero_ventas,
       SUM(d.cantidad * d.precio_unitario) AS ingresos
FROM ventas v
JOIN detalle_venta d ON v.id_venta = d.id_venta
GROUP BY mes
ORDER BY mes;

-- 8. Proveedores con cuantos productos nos dan y el valor de ese inventario
SELECT pr.nombre AS proveedor, pr.ciudad,
       COUNT(p.id_producto) AS productos,
       SUM(p.precio * p.stock) AS valor_inventario
FROM proveedores pr
JOIN productos p ON pr.id_proveedor = p.id_proveedor
GROUP BY pr.nombre, pr.ciudad
ORDER BY valor_inventario DESC;

-- 9. Clientes que han gastado mas de 50 soles (uso de HAVING)
SELECT cl.nombre AS cliente,
       SUM(d.cantidad * d.precio_unitario) AS total_gastado
FROM clientes cl
JOIN ventas v        ON cl.id_cliente = v.id_cliente
JOIN detalle_venta d ON v.id_venta = d.id_venta
GROUP BY cl.nombre
HAVING SUM(d.cantidad * d.precio_unitario) > 50;

-- 10. Clientes que todavia no han comprado nada (LEFT JOIN)
SELECT cl.nombre AS cliente, cl.telefono
FROM clientes cl
LEFT JOIN ventas v ON cl.id_cliente = v.id_cliente
WHERE v.id_venta IS NULL;

-- Extra: productos con stock bajo y a que proveedor hay que pedirle
SELECT p.nombre AS producto, p.stock, pr.nombre AS proveedor, pr.telefono
FROM productos p
JOIN proveedores pr ON p.id_proveedor = pr.id_proveedor
WHERE p.stock <= 5;
