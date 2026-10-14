# API REST de productos con Flask y SQLite
# Para ejecutar:  pip install flask   y luego   python app.py
# Despues abrir http://localhost:5000 en el navegador

import sqlite3
from flask import Flask, request, jsonify

app = Flask(__name__)
BASE_DATOS = "productos.db"


def conectar():
    conexion = sqlite3.connect(BASE_DATOS)
    conexion.row_factory = sqlite3.Row  # para poder usar fila["nombre"]
    return conexion


def crear_tabla():
    conexion = conectar()
    conexion.execute("""
        CREATE TABLE IF NOT EXISTS productos (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            nombre TEXT NOT NULL,
            precio REAL NOT NULL,
            stock INTEGER NOT NULL
        )
    """)
    conexion.commit()
    conexion.close()


# Revisa que los datos que llegan esten bien. Devuelve un mensaje de error o None
def validar(datos):
    if datos is None:
        return "Faltan los datos"
    if "nombre" not in datos or str(datos["nombre"]).strip() == "":
        return "El nombre es obligatorio"
    try:
        precio = float(datos["precio"])
        stock = int(datos["stock"])
    except (KeyError, ValueError, TypeError):
        return "Precio y stock deben ser numeros"
    if precio < 0 or stock < 0:
        return "Precio y stock no pueden ser negativos"
    return None


# Pagina principal (el HTML que consume la API)
@app.route("/")
def inicio():
    return app.send_static_file("index.html")


# LISTAR todos los productos
@app.route("/api/productos", methods=["GET"])
def listar():
    conexion = conectar()
    filas = conexion.execute("SELECT * FROM productos").fetchall()
    conexion.close()
    productos = []
    for fila in filas:
        productos.append(dict(fila))
    return jsonify(productos)


# VER un solo producto
@app.route("/api/productos/<int:id>", methods=["GET"])
def obtener(id):
    conexion = conectar()
    fila = conexion.execute("SELECT * FROM productos WHERE id = ?", (id,)).fetchone()
    conexion.close()
    if fila is None:
        return jsonify({"error": "Producto no encontrado"}), 404
    return jsonify(dict(fila))


# CREAR un producto
@app.route("/api/productos", methods=["POST"])
def crear():
    datos = request.get_json(silent=True)
    error = validar(datos)
    if error:
        return jsonify({"error": error}), 400

    conexion = conectar()
    cursor = conexion.execute(
        "INSERT INTO productos (nombre, precio, stock) VALUES (?, ?, ?)",
        (datos["nombre"].strip(), float(datos["precio"]), int(datos["stock"]))
    )
    conexion.commit()
    nuevo_id = cursor.lastrowid
    conexion.close()
    return jsonify({"id": nuevo_id, "mensaje": "Producto creado"}), 201


# EDITAR un producto
@app.route("/api/productos/<int:id>", methods=["PUT"])
def editar(id):
    datos = request.get_json(silent=True)
    error = validar(datos)
    if error:
        return jsonify({"error": error}), 400

    conexion = conectar()
    cursor = conexion.execute(
        "UPDATE productos SET nombre = ?, precio = ?, stock = ? WHERE id = ?",
        (datos["nombre"].strip(), float(datos["precio"]), int(datos["stock"]), id)
    )
    conexion.commit()
    filas_cambiadas = cursor.rowcount
    conexion.close()

    if filas_cambiadas == 0:
        return jsonify({"error": "Producto no encontrado"}), 404
    return jsonify({"mensaje": "Producto actualizado"})


# BORRAR un producto
@app.route("/api/productos/<int:id>", methods=["DELETE"])
def borrar(id):
    conexion = conectar()
    cursor = conexion.execute("DELETE FROM productos WHERE id = ?", (id,))
    conexion.commit()
    filas_borradas = cursor.rowcount
    conexion.close()

    if filas_borradas == 0:
        return jsonify({"error": "Producto no encontrado"}), 404
    return jsonify({"mensaje": "Producto eliminado"})


if __name__ == "__main__":
    crear_tabla()
    app.run(debug=True)
